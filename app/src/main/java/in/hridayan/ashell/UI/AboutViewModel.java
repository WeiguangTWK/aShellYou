package in.hridayan.ashell.UI;

import androidx.lifecycle.ViewModel;

public class AboutViewModel extends ViewModel {
  private boolean isToolbarExpanded = true;

  public boolean isToolbarExpanded() {
    return isToolbarExpanded;
  }

  public void setToolbarExpanded(boolean toolbarExpanded) {
    isToolbarExpanded = toolbarExpanded;
  }
}
