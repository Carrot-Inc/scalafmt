package org.scalafmt
package config

import metaconfig._

/** Configuration option for aligning tokens.
  *
  * @param code
  *   string literal value of the token to align by.
  * @param owners
  *   array of owner specs.
  */

case class AlignToken(
    code: String,
    owners: Seq[TreePattern] = Seq.empty,
    // CARROT fork: when true, this token participates in alignment only if
    // its enclosing clause has a source break after the open delimiter (i.e.
    // config-style layout); tokens in inline-start clauses are left alone.
    onlyIfClauseBroken: Boolean = false,
    // CARROT fork: when true, the owner must be a modifier-less parameter of
    // a `case` class or enum-case primary constructor (plain fields); val/var
    // params and non-case classes are left alone.
    onlyIfCaseClassParam: Boolean = false,
    // CARROT fork: when true, this token participates in alignment only when
    // its output line begins with the owner statement's first token (e.g. an
    // enum case's `case` keyword); continuation lines — like a multiline
    // clause's dangling close paren — are never padded.
    onlyIfOwnerStartsLine: Boolean = false,
) {
  def getMatcher: Seq[TreePattern.Matcher] = owners.distinct.map(_.getMatcher)
}

object AlignToken {

  def apply(code: String, owner: String): AlignToken = {
    val owners = Option(owner) match {
      case None => Seq.empty
      case x => Seq(TreePattern(x))
    }
    AlignToken(code, owners)
  }

  implicit lazy val surface: generic.Surface[AlignToken] = generic
    .deriveSurface[AlignToken]
  implicit lazy val encoder: ConfEncoder[AlignToken] = generic.deriveEncoder
  val applyInfix = "Term.ApplyInfix"
  val caseArrow = AlignToken("=>", "Case")
  protected[scalafmt] val fallbackAlign = new AlignToken("<empty>")
  implicit val decoder: ConfDecoderEx[AlignToken] = generic
    .deriveDecoderEx[AlignToken](fallbackAlign).noTypos.withSectionRenames(
      // deprecated since v3.0.0
      annotation.SectionRename { case x: Conf.Str =>
        Conf.Lst(Conf.Obj("regex" -> x))
      }("owner", "owners"),
    ).except {
      case (_, Conf.Str("caseArrow")) => Some(Configured.Ok(caseArrow))
      case (_, Conf.Str(regex)) => Some(
          Configured.Ok(default.findOrNull(_.code == regex) ?? AlignToken(regex)),
        )
      case _ => None
    }
  val seqDecoder: ConfDecoderEx[Seq[AlignToken]] = implicitly

  val default = Seq(
    caseArrow,
    AlignToken("extends", raw"Template|Defn\.EnumCase"),
    AlignToken("//"),
    AlignToken("{", "Template"),
    AlignToken("}", "Template"),
    AlignToken("%", applyInfix),
    AlignToken("%%", applyInfix),
    AlignToken("%%%", applyInfix),
    AlignToken("⇒", "Case"),
    AlignToken("<-", "Enumerator.Generator"),
    AlignToken("←", "Enumerator.Generator"),
    AlignToken("->", applyInfix),
    AlignToken("→", applyInfix),
    AlignToken(":=", applyInfix),
    AlignToken("=", "(Enumerator.Val|Defn.(Va(l|r)|GivenAlias|Def|Type))"),
  )

}
