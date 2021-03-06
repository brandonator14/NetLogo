// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.review

import java.awt.Color.{ GRAY, WHITE }

import org.nlogo.mirror.FakeWorld
import org.nlogo.window

import javax.swing.JPanel

class InterfacePanel(val reviewTab: ReviewTab)
  extends JPanel
  with HasPlotPanels {

  def repaintView(g: java.awt.Graphics, viewArea: java.awt.geom.Area) {
    for {
      run <- reviewTab.state.currentRun
      frame <- run.currentFrame
      fakeWorld = new FakeWorld(frame.mirroredState)
      paintArea = new java.awt.geom.Area(getBounds())
      viewSettings = run.fixedViewSettings
      g2d = g.create.asInstanceOf[java.awt.Graphics2D]
    } {
      paintArea.intersect(viewArea) // avoid spilling outside interface panel
      try {
        g2d.setClip(paintArea)
        g2d.translate(viewArea.getBounds.x, viewArea.getBounds.y)
        val renderer = fakeWorld.newRenderer
        renderer.trailDrawer.readImage(frame.drawingImage)
        renderer.paint(g2d, viewSettings)
      } finally {
        g2d.dispose()
      }
    }
  }

  def repaintWidgets(g: java.awt.Graphics) {
    for {
      frame <- reviewTab.state.currentFrame
      values = frame.mirroredState
        .filterKeys(_.kind == org.nlogo.mirror.Mirrorables.WidgetValue)
        .toSeq
        .sortBy { case (agentKey, vars) => agentKey.id } // should be z-order
        .map { case (agentKey, vars) => vars(0).asInstanceOf[String] }
      (w, v) <- reviewTab.widgetHooks.map(_.widget) zip values
    } {
      val g2d = g.create.asInstanceOf[java.awt.Graphics2D]
      try {
        val container = reviewTab.ws.viewWidget.findWidgetContainer
        val bounds = container.getUnzoomedBounds(w)
        g2d.setRenderingHint(
          java.awt.RenderingHints.KEY_ANTIALIASING,
          java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setFont(w.getFont)
        g2d.clipRect(bounds.x, bounds.y, w.getSize().width, w.getSize().height) // make sure text doesn't overflow
        g2d.translate(bounds.x, bounds.y)
        w match {
          case m: window.MonitorWidget =>
            window.MonitorPainter.paint(
              g2d, m.getSize, m.getForeground, m.displayName, v)
          case _ => // ignore for now
        }
      } finally {
        g2d.dispose()
      }
    }
  }

  override def paintComponent(g: java.awt.Graphics) {
    super.paintComponent(g)
    g.setColor(if (reviewTab.state.currentRun.isDefined) WHITE else GRAY)
    g.fillRect(0, 0, getWidth, getHeight)
    for {
      run <- reviewTab.state.currentRun
    } {
      g.drawImage(run.interfaceImage, 0, 0, null)
      repaintView(g, run.viewArea)
      repaintWidgets(g)
      refreshPlotPanels()
    }
  }
}
