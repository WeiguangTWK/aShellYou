package in.hridayan.ashell.viewmodels;

import android.util.Pair;
import java.util.List;
import androidx.lifecycle.ViewModel;

public class AshellFragmentViewModel extends ViewModel {

  private boolean isSaveButtonVisible, isEndIconVisible;
  private String commandText;
  private static final int nullValue = 2004;
  private List<String> shellOutput, history;
  private int scrollPosition, sendDrawable = nullValue;
  private Pair<Integer, Integer> rvPositionAndOffset;

  public List<String> getShellOutput() {
    return shellOutput;
  }

  public void setShellOutput(List<String> shellOutput) {
    this.shellOutput = shellOutput;
  }

  public void setRVPositionAndOffset(Pair<Integer, Integer> pair) {
    this.rvPositionAndOffset = pair;
  }

  public Pair<Integer, Integer> getRVPositionAndOffset() {
    return rvPositionAndOffset;
  }

  public List<String> getHistory() {
    return history;
  }

  public void setHistory(List<String> history) {
    this.history = history;
  }

  public int getScrollPosition() {
    return scrollPosition;
  }

  public void setScrollPosition(int scrollPosition) {
    this.scrollPosition = scrollPosition;
  }

  public String getCommandText() {
    return commandText;
  }

  public void setCommandText(String commandText) {
    this.commandText = commandText;
  }

  public boolean isSaveButtonVisible() {
    return isSaveButtonVisible;
  }

  public void setSaveButtonVisible(boolean saveButtonVisible) {
    isSaveButtonVisible = saveButtonVisible;
  }

  public int getSendDrawable() {
    return sendDrawable;
  }

  public void setSendDrawable(int drawable) {
    sendDrawable = drawable;
  }

  public boolean isSendDrawableSaved() {
    return sendDrawable != nullValue;
  }

  public boolean isEndIconVisible() {
    return isEndIconVisible;
  }

  public void setEndIconVisible(boolean endIconVisible) {
    isEndIconVisible = endIconVisible;
  }
}
