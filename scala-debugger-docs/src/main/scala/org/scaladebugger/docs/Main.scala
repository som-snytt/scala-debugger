package org.scaladebugger.docs

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Main entrypoint for generating and serving docs.
 */
object Main {
  /** Logger for this class. */
  private lazy val logger = new Logger(this.getClass)

  def main(args: Array[String]): Unit = {
    val config = new Config(args)

    // Set global logger used throughout program
    Logger.setDefaultLevel(config.defaultLogLevel())

    // Generate before other actions if indicated
    if (config.generate()) {
      new Generator(config).run()
    }

    // Serve generated content
    if (config.serve()) {
      val rootPath = Paths.get(config.inputDir())

      val watcherThread = if (config.liveReload()) {
        logger.log(s"Watching $rootPath for changes")
        val watcher = new Watcher(
          path = rootPath,
          callback = (rootPath, events) => {
            logger.verbose(s"Detected ${events.length} change(s) at $rootPath")
            new Generator(config).run()
          },
          waitTime = config.liveReloadWaitTime(),
          waitUnit = TimeUnit.MILLISECONDS
        )

        Some(watcher.runAsync())
      } else None

      new Server(config).run()

      watcherThread.foreach(t => {
        logger.verbose("Shutting down watcher thread")
        t.interrupt()
      })

    // Publish generated content
    } else if (config.publish()) {
      new Publisher(config).run()

    // Print help info
    } else if (!config.generate()) {
      config.printHelp()
    }
  }
}
