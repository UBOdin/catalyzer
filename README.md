# Catalyser

### What

Catalyzer is an educational platform for teaching database systems.  Simply put, it's a gutted version of the Apache Spark Catalyst SQL engine, designed to allow students to re-implement key features of Catalyst.  

##### Install

Import the Catalyzer library.  For SBT, add the following to your `build.sbt`:
```
resolvers += "MimirDB" at "https://maven.mimirdb.info/"
libraryDependencies += "edu.buffalo.cse.odin" %% "catalyzer" % "3.0"
```

##### Parse SQL

Catalyzer includes Spark's SQLParser.  Using it is not mandatory, but the following code snippet will parse a SQL string into a `LogicalPlan`

```
import org.apache.spark.sql.execution.SparkSqlParser
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan

...
  def parseSql(sql: String): LogicalPlan = 
    new SparkSqlParser().parsePlan(sql)
...
```
`SparkSqlParser` will parse any SQL expresion, including both queries (`SELECT ...`), as well as DML/DDL commands (`INSERT INTO ...`, `CREATE TABLE(...)`, etc...).  Note that while the latter are flagged as `LogicalPlan` nodes, they can not generally be evaluated.

Also note that `SparkSqlParser` is reusable (you don't need to create a new one for every parse).

##### Analysis

Shortly after parsing, Spark applies a two-part process called analysis:
1. **Resolution**: Placeholder AST nodes are replaced by the real elements
    1. Human-facing string-typed identifiers are "wired together" using Spark-internal identifiers (`exprId`) or replaced by 
    2. **Nodes with unknown types in the `Expression` and `LogicalPlan` ASTs are replaced by the corresponding typed values.
2. **Validation**: Spark checks to see if all of the types "line up" (e.g., no arithmetic between integers and strings)

Spark's validation logic is mostly intact, but you'll need to implement the Resolution step yourself.  In particular, Spark's SQL Parser leaves behind "placeholder" nodes in both the `Expression` and `LogicalPlan` ASTs, whenever the user references something by a string.  Normally, the analysis step replaces these placeholders with something that can actually be used.  Placeholders that you can expect to encounter are listed below.

Before we discuss placeholders, we need to take a brief digression to explain the `exprId` field in many `Expression` AST nodes.  For example: 
```
case class AttributeReference(
    name: String,
    dataType: DataType,
    nullable: Boolean = true,
    override val metadata: Metadata = Metadata.empty)(
    val exprId: ExprId = NamedExpression.newExprId,
    val qualifier: Seq[String] = Seq.empty[String])
  extends Attribute with Unevaluable {
```
In most node types, the `exprId` field is allocated automatically with a fresh identifier (i.e., using `NamedExpression.newExprId`) when the node is created.  Spark uses `exprId`s internally to keep track of which expressions line up with which other expressions.  Two `Attribute`s are the same if and only if they have the same `exprId` (whether their names are the same or different does not matter).

The other thing to discuss before we move on is resolution.  `Expression` provides a `.resolved` method that checks to see whether the expression has been fully resolved.  The `dataType` method will not work until `resolved` returns true.  `resolved`, in particular checks for three things:
* All descendents of the node must be resolved
* The node itself must not be an Unresolved___
* The node's children must have a `dataType` compatible with the node itself.

The last condition is especially tricky, but you can call e.checkInputDataTypes() on each node of the tree to check for errors (see below).

That all being said, here are unresolved nodes you can expect to encounter:
###### `LogicalPlan`:
```
case class UnresolvedRelation(
    nameElements: Seq[String], 
    options: CaseInsensitiveStringMap, 
    isStreaming: Boolean)
```
This class is used when a relation is referenceed in a `LogicalPlan` in SQL (typically the `FROM` clause of a `SELECT`).  The `nameElements` field encodes the `'.'`-separated elements of the table name (e.g., `foo.bar` would be encoded as `Seq("foo", "bar")`).  Under typical use, this sequence will always have only one element.  Name elements are case-insensitive.

Ocurrences of this class will need to be replaced during analysis with an AST node that knows what attributes the corresponding table has.  Spark has several built-in `LogicalPlan` nodes that can be used to encode tables, but you might find it easier to just create your own subclass of `LeafNode` to represent a table node.

A subclass of `LeafNode` only needs to implement one field:
```
case class ____(____) 
  extends LeafNode
{
  val output: AttributeSet = ???
}
```
Note that `AttributeSet` is a subclass of `Seq[Attribute]`.  In general, the  output field should be given as a sequence of `AttributeReference`s (see above).

###### `Expression`:
```
case class UnresolvedStar(target: Option[Seq[String]])
```
Any asterisk `*` appearing in a SQL is translated into this class.  Generally, this happens in three places:
1. `SELECT * FROM foo` → `Project(Seq(UnresolvedStar(None)), ...)`
2. `SELECT foo.* FROM foo, bar` → `Project(Seq(UnresolvedStar(Some(Seq("foo")))), ...)`
3. `SELECT COUNT(*) FROM FOO` → `Aggregate(..., Seq(Count(Seq(UnresolvedStar(None)))), ...)`
The first two cases are special, as they both represent multiple fields.  You'll need to expand these out during the analysis phase.  Note that like `UnresolvedRelation`, table names are Sequences of `.`-separated strings.

---

```
case class UnresolvedAttribute(nameParts: Seq[String])
```
An attribute name that hasn't been "wired up" with an `exprId`.  In general, there are two cases that need to be handled during Analysis:
* `attribute` → `UnresolvedAttribute(Seq("attribute"))`
* `relation.attribute` → `UnresolvedAttribute(Seq("relation", "attribute"))`

For resolving attributes, keep in mind that each operator (that has been resolved already) knows its output schema (typically computing it from the input schema): 
```
val attributes: AttributeSet = source.output
```
As above, note that `AttributeSet` is a subclass of `Seq[Attribute]`

In general, you can expect the contents of this sequence to consist of:
* `AttributeReference`
* `UnresolvedAttribute`
and assuming that you've done your analysis job right, you should only see `AttributeReference`s.

One additional thing that may be helpful in resolving `UnresolvedAttribute`s is that `AttributeReference` has a qualifiers field Spark uses to store the table name.  This field is automatically managed in nested subqueries, but keep in mind that if you're using a custom table class (as suggested above), you will need to set this field yourself when declaring the table's output there.

##### Evaluation

Although `Expression` provides an `eval` method, `LogicalPlan` does not.  To evaluate `LogicalPlan`s, you need to compile them.

The most straightforward way to implement a Relational Algebra plan is a so called "pull"-based model that you might already be familiar with: `Iterator`s.  This is the starting point for Spark, and many other relational database engine's runtimes.

Implementing an iterator typically involves two things:
```
class MyIterator extends Iterator[MyFoo]
{
    def hasNext: Boolean = /* return true if there are more elements */
    def next: MyFoo = /* assemble and return the MyFoo instance */
}
```
For example, here's a simple one that iterates over a range of values.
```
class Range(low: Int, high: Int) extends Iterator[Int]
{
    var current = low
    def hasNext = current < high
    def next: Int = { val ret = current; current += 1; return ret }
}
```


### Documentation

##### Useful `LogicalPlan` Features
Spark's LogicalPlan AST provides several useful tools

##### Useful `Expression` Features

Spark's Expression AST provides several useful tools:

1. Primitive-valued evaluation logic
```
val row = InternalRow.fromSeq( Seq(1, 2, "bob") )
val literal = expression.eval(row)
```
2. Type inference (although note that this won't work until after **Resolution**)
```
val dataType:DataType = expression.dataType
```
3. Type validation
```
expression.checkInputDataTypes() match {
    case TypeCheckFailure(message) => /* do something */
    case TypeCheckSuccess() => /* do something */
}
```
4. Reference Management
```
val attributes:AttributeSet = expression.attributes
```
5. Pattern-based tree replacements:
```
val rewritten = 
  expression.transform { 
    case Add(Literal(lhs, IntType), Literal(rhs, IntType)) => 
      Literal(lhs.asInstanceOf[Int] + rhs.asInstanceOf[Int])
  }
```


### Details

Catalyzer is a large fragment of the Apache Spark 3.0.1 catalyst database complier decoupled from the rest of Spark.  Concretely, what remains is:
- The SQL parser.
- Most of the `Expression` AST (`org.apache.spark.sql.catalyst.expressions._`)
- Most of the `LogicalPlan` AST (`org.apache.spark.sql.catalyst.plans.logical._`)

The API of the above classes should be largely unchanged.  Assorted goo required to minimize changes to the above is also included, although several supporting classes have been heavily modified to remove dependencies on other parts of Spark.  

Most notably, the following items have been removed in Catalyzer:
- Most of the Analysis logic (`org.apache.spark.sql.catalyst.analysis._`)
- All of the Optimizer rules (`org.apache.spark.sql.catalyst.optimizer._`)
- All of the plan-level runtime logic (`org.apache.spark.sql.catalyst.plans.physical._`, `org.apache.spark.rdd._`, etc...)


### License

Contains code from [Spark 3.0.1, released under the Apache 2.0 License.](https://github.com/apache/spark/blob/a82aee044127825ffefa0ed09b0ae5b987b9dd21/LICENSE)

Modifications released under the same license.
