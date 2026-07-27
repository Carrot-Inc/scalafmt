package org.scalafmt.config

import metaconfig._

/** @param main
  *   the primary indentation used in the code
  * @param significant
  *   the indentation used when optional braces are omitted
  * @param defnSite
  *   indentation around class/def
  * @param ctorSite
  *   indentation around class constructor parameters
  * @param caseSite
  *   indentation for case values before arrow
  * @param callSite
  *   indentation around function calls, etc.
  * @param extendSite
  *   indentation before `extends`
  * @param withSiteRelativeToExtends
  *   additional indentation before `with`
  * @param commaSiteRelativeToExtends
  *   additional indentation before in the line after extends with a comma
  * @param yieldKeyword
  *   - If true, indents `yield` by two spaces
  *     {{{
  *       for (i <- j)
  *         yield banana
  *     }}}
  *   - If false, treats `yield` like `else`
  *     {{{
  *       for (i <- j)
  *       yield banana
  *     }}}
  */
case class Indents(
    main: Int = 2,
    private[config] val significant: Option[Int] = None,
    callSite: Int = 2,
    ctrlSite: Option[Int] = None,
    private[config] val binPackCallSite: Option[Int] = None,
    private[config] val defnSite: Int = 4,
    binPackDefnSite: Option[Int] = None,
    caseSite: Int = 4,
    // CARROT fork: when true, control-structure body indents (case-arrow,
    // then/else) are attached only to splits that break after the keyword;
    // a body starting on the keyword line anchors its continuation lines
    // (e.g. select chains) at the statement level, like `=` bodies, instead
    // of stacking the body indent. Under newlines.source=keep this also lets
    // a multiline then/else body start on the keyword line.
    ctrlBodyIndentOnlyIfBroken: Boolean = false,
    // CARROT fork: under newlines.source=keep, a leading `|` continuation
    // line in a multiline case pattern keeps its source column offset
    // relative to the `case` keyword instead of the fixed caseSite indent.
    preservePatAltIndent: Boolean = false,
    // CARROT fork: under newlines.source=keep, a source break before a
    // subsequent defn-site parameter clause is kept, with the clause's `(`
    // keeping its source column offset relative to the defn statement
    // (e.g. hand-aligned under the first clause's paren).
    preserveParamClauseIndent: Boolean = false,
    // CARROT fork: under newlines.source=keep, a source break before the `=`
    // of a definition is kept, with the `=` line keeping its source column
    // offset relative to the statement.
    preserveAssignIndent: Boolean = false,
    matchSite: Option[Int] = None,
    private[config] val ctorSite: Option[Int] = None,
    extraBeforeOpenParenDefnSite: Int = 0,
    relativeToLhsLastLine: Seq[Indents.RelativeToLhs] = Nil,
    private[config] val fewerBraces: Indents.FewerBraces =
      Indents.FewerBraces.never,
    private[config] val afterInfixSite: Option[Int] = None,
    @annotation.ExtraName("deriveSite")
    extendSite: Int = 4,
    withSiteRelativeToExtends: Int = 0,
    commaSiteRelativeToExtends: Int = 2,
    yieldKeyword: Boolean = true,
    infix: Seq[IndentOperator] = Seq(IndentOperator.default),
) {
  val getSignificant = significant.getOrElse(main)

  def getDefnSite(tree: meta.Tree): Int = (tree match {
    case _: meta.Member.ParamClause | _: meta.Member.ParamClauseGroup => tree
        .parent.map(getDefnSite)
    case _: meta.Ctor => ctorSite
    case _ => None
  }).getOrElse(defnSite)

  def getAfterInfixSite: Int = afterInfixSite.getOrElse(main)
  def getBinPackCallSite: Int = binPackCallSite.getOrElse(callSite)
  def getBinPackCallSites: (Int, Int) = (callSite, getBinPackCallSite)
  def getBinPackDefnSites(tree: meta.Tree): (Int, Int) = {
    val len = getDefnSite(tree)
    (len, binPackDefnSite.getOrElse(len))
  }
}

object Indents {
  implicit lazy val surface: generic.Surface[Indents] = generic.deriveSurface
  implicit lazy val codec: ConfCodecEx[Indents] = generic.deriveCodecEx(Indents())
    .noTypos.withSectionRenames(
      annotation.SectionRename.partial {
        case x @ Conf.Obj(head :: rest) if rest.nonEmpty || head._1 != "+" =>
          Conf.Lst(x)
      }("infix", "infix"),
    )

  sealed abstract class RelativeToLhs
  object RelativeToLhs {
    case object `match` extends RelativeToLhs
    case object `infix` extends RelativeToLhs

    implicit val reader: ConfCodecEx[RelativeToLhs] = ConfCodecEx
      .oneOf[RelativeToLhs](`match`, `infix`)
  }

  sealed abstract class FewerBraces
  object FewerBraces {
    case object never extends FewerBraces
    case object always extends FewerBraces
    case object beforeSelect extends FewerBraces

    implicit val reader: ConfCodecEx[FewerBraces] = ConfCodecEx
      .oneOf[FewerBraces](never, always, beforeSelect)
  }

}
