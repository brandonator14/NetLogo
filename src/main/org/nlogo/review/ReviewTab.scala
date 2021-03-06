// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.review

import java.awt.BorderLayout

import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter

import org.nlogo.api
import org.nlogo.awt.UserCancelException
import org.nlogo.mirror.ModelRunIO
import org.nlogo.util.Exceptions.ignoring
import org.nlogo.window
import org.nlogo.window.{ InvalidVersionException, ModelLoader, MonitorWidget, Widget }

import javax.swing.{ JOptionPane, JPanel, JScrollPane, JSplitPane }
import javax.swing.event.{
  ChangeEvent,
  ChangeListener,
  DocumentEvent,
  DocumentListener,
  TableModelEvent,
  TableModelListener
}

case class WidgetHook(
  val widget: Widget,
  val valueStringGetter: () => String)

class ReviewTab(
  val ws: window.GUIWorkspace,
  val saveModel: () => String,
  offerSave: () => Unit,
  selectReviewTab: () => Unit)
  extends JPanel
  with window.ReviewTabInterface {

  val state = new ReviewTabState()

  def workspaceWidgets =
    Option(ws.viewWidget.findWidgetContainer)
      .toSeq.flatMap(_.getWidgetsForSaving.asScala)

  def widgetHooks = workspaceWidgets
    .collect { case m: MonitorWidget => m }
    .map(m => WidgetHook(m, () => m.valueString))

  val runList = new RunList(this)

  val runRecorder = new RunRecorder(
    ws, state, saveModel, () => widgetHooks,
    () => disableRecording)

  override def loadedRuns: Seq[api.ModelRun] = state.runs
  override def loadRun(inputStream: java.io.InputStream): Unit = {
    val run = ModelRunIO.load(inputStream)
    loadModelIfNeeded(run.modelString)
    state.addRun(run)
  }
  override def currentRun: Option[api.ModelRun] = state.currentRun

  def userConfirms(title: String, message: String) =
    JOptionPane.showConfirmDialog(ReviewTab.this, message,
      title, JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION

  def enableRecording() {
    state.recordingEnabled = true
    reviewToolBar.enabledCheckBox.setSelected(state.recordingEnabled)
  }

  def disableRecording() {
    state.recordingEnabled = false
    reviewToolBar.enabledCheckBox.setSelected(state.recordingEnabled)
  }

  val notesTabbedPane = new NotesTabbedPane(state)
  val scrubberPanel = new ScrubberPanel(
    notesTabbedPane.indexedNotesTable,
    () => state.currentFrameIndex,
    () => state.currentTicks,
    state,
    runRecorder)
  val reviewToolBar = new ReviewToolBar(this)
  val interfacePanel = new InterfacePanel(this)

  scrubberPanel.scrubber.addChangeListener(new ChangeListener {
    def stateChanged(evt: ChangeEvent) {
      val newFrame = scrubberPanel.scrubber.getValue
      state.currentRun.foreach(_.currentFrameIndex = Some(newFrame))
      notesTabbedPane.indexedNotesTable.scrollTo(newFrame)
      interfacePanel.repaint()
    }
  })

  // TODO: this should probably be in state
  notesTabbedPane.generalNotes.getDocument.addDocumentListener(new DocumentListener {
    private def updateGeneralNotesInRun() {
      for (run <- state.currentRun) {
        run.generalNotes = notesTabbedPane.generalNotes.getText
        reviewToolBar.saveButton.setEnabled(run.dirty)
      }
    }
    def insertUpdate(e: DocumentEvent) { updateGeneralNotesInRun() }
    def removeUpdate(e: DocumentEvent) { updateGeneralNotesInRun() }
    def changedUpdate(e: DocumentEvent) { updateGeneralNotesInRun() }
  })

  // TODO: this should probably be in state
  notesTabbedPane.indexedNotesTable.getModel.addTableModelListener(new TableModelListener {
    override def tableChanged(event: TableModelEvent) {
      for (run <- state.currentRun) {
        run.indexedNotes = notesTabbedPane.indexedNotesTable.model.notes
        reviewToolBar.saveButton.setEnabled(run.dirty)
      }
    }
  })

  def loadModelIfNeeded(modelString: String) {
    val currentModelString = saveModel()
    if (modelString != currentModelString) {
      offerSave()
      ModelLoader.load(ReviewTab.this, null, api.ModelType.Library, modelString)
      selectReviewTab()
    }
  }

  object RunListPanel extends JPanel {
    setLayout(new BorderLayout)
    add(new JScrollPane(runList), BorderLayout.CENTER)
  }

  object InterfaceScrollPane extends JScrollPane {
    setViewportView(interfacePanel)
  }

  object RunPanel extends JPanel {
    setLayout(new BorderLayout)
    add(InterfaceScrollPane, BorderLayout.CENTER)
    add(scrubberPanel, BorderLayout.SOUTH)
  }

  object PrimarySplitPane extends JSplitPane(
    JSplitPane.VERTICAL_SPLIT,
    SecondarySplitPane,
    notesTabbedPane) {
    setResizeWeight(0.8)
    setDividerLocation(400)
  }

  object SecondarySplitPane extends JSplitPane(
    JSplitPane.HORIZONTAL_SPLIT,
    RunListPanel,
    RunPanel) {
    setResizeWeight(0.0)
    setDividerLocation(200)
  }

  locally {
    setLayout(new BorderLayout)
    add(reviewToolBar, BorderLayout.NORTH)
    add(PrimarySplitPane, BorderLayout.CENTER)
  }
}

object ReviewTab {
  def removeExtension(path: String) = new java.io.File(path).getName.replaceAll("\\.[^.]*$", "")
}