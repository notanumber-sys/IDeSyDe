package idesyde.identification

import java.util.Optional
import scala.jdk.OptionConverters._

/** This class exist to ensure compatibility with Java, as the tuples can become awkward in other
  * languages other than scala. It changes nothing in the concepts of the identification process,
  * but garantees that the tooling will remain understandable outside of Scala and in other JVM
  * languages.
  *
  * In summary, this class is equivalent to a tuple (isFixed, identifiedModel), where
  * identifiedModel can be null and isFixed is a boolean.
  */
final case class IdentificationResult[M <: DecisionModel](
    private val _fixed: Boolean = false,
    private val _identified: Option[M] = Option.empty
) {

  def this(identTuple: (Boolean, Option[M])) = {
    this(identTuple._1, identTuple._2)
  }

  def this(fixed: Boolean, idenfitied: M) = {
    this(fixed, Option(idenfitied))
  }

  def identified: Option[M] = _identified

  def getIdentified(): Optional[M] = _identified.toJava

  def hasIdentified(): Boolean = _identified.isDefined

  def isFixed(): Boolean = _fixed


}

object IdentificationResult {
  def unapply[M <: DecisionModel](identificationResult: IdentificationResult[M]) =
    (identificationResult.isFixed(), identificationResult.identified)

  def apply[M <: DecisionModel](fixed: Boolean, identified: M) = new IdentificationResult(fixed, identified)

  def apply[M <: DecisionModel](fixed: Boolean, identified: Option[M]) = new IdentificationResult(fixed, identified)

  def apply[M <: DecisionModel](identTuple: (Boolean, Option[M])) = new IdentificationResult(identTuple._1, identTuple._2)

  def fixedEmpty[M <: DecisionModel]() = new IdentificationResult(true, Option.empty[M])

  def unfixedEmpty[M <: DecisionModel]() = new IdentificationResult(false, Option.empty[M])

  def fixed[M <: DecisionModel](identified: M) = new IdentificationResult(true, identified)

  def unfixed[M <: DecisionModel](identified: M) = new IdentificationResult(true, identified)

}
