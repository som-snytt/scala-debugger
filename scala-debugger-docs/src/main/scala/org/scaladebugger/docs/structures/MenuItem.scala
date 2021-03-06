package org.scaladebugger.docs.structures

import java.nio.file.{Files, Path, Paths}

import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.{FalseFileFilter, TrueFileFilter}
import org.scaladebugger.docs.Config
import org.scaladebugger.docs.utils.FileHelper

import scala.annotation.tailrec

/**
 * Represents a generic menu item.
 *
 * @param name The name of the menu item
 * @param link The link for the menu item, or None if the menu item does not
 *             link to any content
 * @param children The children of the menu item
 * @param selected Whether or not the menu item is selected
 * @param weight The weight associated with the menu item, used for ordering
 *               where smaller weights should appear first (left/top) and
 *               larger weights should appear last (right/bottom)
 * @param fake If marked as fake, indicates that the menu item should not be
 *             used and is around for other purposes
 */
case class MenuItem(
  name: String,
  link: Option[String] = None,
  children: Seq[MenuItem] = Nil,
  selected: Boolean = false,
  weight: Double = MenuItem.DefaultWeight,
  fake: Boolean = false
) {
  /**
   * Indicates whether or not the menu item represents the specified page.
   *
   * @param page The page to compare to the menu item
   * @return True if the menu item represents the page, otherwise false
   */
  def representsPage(page: Page): Boolean = {
    if (page.isDirectory) page.name == name
    else page.title == name
  }

  /**
   * Indicates whether or not this menu item or any of its children (or one of
   * its their children, recursive) is selected.
   *
   * @return True if this menu item or any child of this menu item or any one
   *         of their children (recursive) is selected, otherwise false
   */
  def isDirectlyOrIndirectlySelected: Boolean = {
    @tailrec def checkIsSelected(menuItems: MenuItem*): Boolean = {
      if (menuItems.isEmpty) false
      else if (menuItems.exists(_.selected)) true
      else checkIsSelected(menuItems.flatMap(_.children): _*)
    }

    checkIsSelected(this)
  }
}

object MenuItem {
  /** Represents the default weight of menu items. */
  val DefaultWeight: Double = 0

  /**
   * Generates a collection of menu items from the given path by searching
   * for markdown files and using them as the basis of children menu items.
   *
   * @param config Used for defaults
   * @param path The path to use as the basis for generating
   *             menu items
   * @param dirUseFirstChild If true, will use the link of the first child
   *                         under the menu item if the menu item's provided
   *                         path is a directory
   * @return The collection of menu items
   */
  def fromPath(
    config: Config,
    path: Path,
    dirUseFirstChild: Boolean = false
  ): Seq[MenuItem] = {
    val mdFiles = FileHelper.markdownFiles(path)

    // Find all directories of src dir
    import scala.collection.JavaConverters._
    val directoryPaths = FileUtils.listFilesAndDirs(
      path.toFile,
      FalseFileFilter.INSTANCE,
      TrueFileFilter.INSTANCE
    ).asScala.map(_.toPath)

    // All paths excluding top-level index.md
    val allPaths: Seq[Path] = (mdFiles ++ directoryPaths)
      .filterNot(p => path.relativize(p) == Paths.get("index.md")).toSeq

    allPaths
      .filter(_.getParent == path)
      .map(p => createLinkedMenuItem(
        config,
        p,
        allPaths,
        dirUseFirstChild = dirUseFirstChild
      ))
      .sortBy(_.weight)
  }

  /**
   * Creates a menu item using the given configuration, series of paths for
   * potential children, and path to be the menu item.
   *
   * @param config Used for defaults
   * @param candidateChildren All paths to consider as children for the new
   *                          menu item
   * @param path The path to serve as the menu item
   * @param dirUseFirstChild If true, will use the link of the first child
   *                         under the menu item if the menu item's provided
   *                         path is a directory
   * @return The new menu item
   */
  private def createLinkedMenuItem(
    config: Config,
    path: Path,
    candidateChildren: Seq[Path],
    dirUseFirstChild: Boolean
  ): MenuItem = {
    val children = candidateChildren.filter(_.getParent == path).map(p =>
      createLinkedMenuItem(config, p, candidateChildren, dirUseFirstChild)
    ).sortBy(_.weight)

    // If the menu item has a fake child, it is an index file, meaning that
    // we want to use that index file to control this menu item's weight,
    // link, etc.
    //
    // E.g. /about/index.md would control the "about" menu item instead of
    //      it being purely from the /about/ directory
    val fakeChild = children.find(_.fake)
    val fakeChildLink = fakeChild.flatMap(_.link).filterNot(_ == "index")
    val normalChildren = children.filterNot(_.fake)

    val page = Page.newInstance(config, path)
    val isDir = page.isDirectory
    val isFake = !isDir && page.metadata.fake

    // Directories don't have metadata, so use default
    val weight = fakeChild.map(_.weight).getOrElse(
      if (isDir) DefaultWeight else page.metadata.weight
    )

    // Directories don't have metadata, so cannot use title
    val name = if (isDir) page.name else page.title

    // Directories use either their index file's link or the first child's link
    //
    // Fake pages and pages not being rendered should not report back a link
    // unless they were given one explicitly
    //
    // Normal pages should return the link provided in their metadata, or
    // their absolute link if no link in the metadata
    val link =
      if (isDir && fakeChildLink.nonEmpty)
        fakeChildLink
      else if (isDir && dirUseFirstChild)
        normalChildren.find(_.link.nonEmpty).flatMap(_.link)
      else if (isDir && !dirUseFirstChild)
        None
      else if (isFake || !page.metadata.render)
        page.metadata.link
      else
        Some(page.metadata.link.getOrElse(page.absoluteLink))

    MenuItem(
      name = name,
      link = link,
      children = normalChildren,
      weight = weight,
      fake = isFake
    )
  }
}
