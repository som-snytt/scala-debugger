package org.scaladebugger.docs.styles

import scalatags.Text.all._
import scalatags.stylesheet._

/**
 * Represents stylesheet for main navigation menu (top bar).
 */
object TopbarNavStyle extends CascadingStyleSheet {
  lazy val headerCls: Cls = cls(
    width := "100%",
    height := "auto"
  )

  lazy val navbar: Cls = cls(
    display := "flex",
    flexDirection := "row",
    flexWrap := "nowrap",
    justifyContent := "space-between",
    alignItems := "center",
    padding := "0.1em 1em",
    fontSize := "1.5em",

    background := "#3B3E43",
    color := "#EBF0F1",
    a(color := "#EBF0F1")
  )

  lazy val navLogo: Cls = cls(
    display := "flex",
    flexDirection := "row",
    flexWrap := "nowrap",
    justifyContent := "flex-start",
    alignItems := "center",
    width := "25%",
    textDecoration := "none",

    img(
      height := "3em",
      paddingRight := "0.5em"
    ),

    span(
      fontSize := "1em",
      whiteSpace := "nowrap"
    )
  )

  lazy val navLinks: Cls = cls(
    width := "70%",

    ul(
      display := "flex",
      flexDirection := "row",
      flexWrap := "nowrap",
      justifyContent := "flex-start",
      listStyleType := "none",
      margin := "0em",
      padding := "0em"
    ),

    li(
      display := "flex",
      alignItems := "center",
      height := "1em",
      padding := "0em 0.5em"
    )
  )

  lazy val navLink: Cls = cls(
    cursor := "pointer",
    color := "#EBF0F1",
    fontSize := "0.65em",
    textDecoration := "none",
    textTransform := "uppercase",
    borderBottom := "1px solid #EBF0F1",
    whiteSpace := "nowrap",
    overflow := "hidden",
    textOverflow := "ellipsis"
  )

  lazy val selectedNavLink: Cls = cls(
    padding := "0em",
    background := "#EBF0F1",

    a(
      color := "#3B3E43",
      borderBottom := "1px solid #3B3E43"
    )
  )
}
