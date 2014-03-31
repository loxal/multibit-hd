package org.multibit.hd.ui.views.wizards.wallet_detail;

import com.google.common.base.Optional;
import net.miginfocom.swing.MigLayout;
import org.multibit.hd.ui.languages.MessageKey;
import org.multibit.hd.ui.views.components.Components;
import org.multibit.hd.ui.views.components.ModelAndView;
import org.multibit.hd.ui.views.components.Panels;
import org.multibit.hd.ui.views.components.panels.PanelDecorator;
import org.multibit.hd.ui.views.components.wallet_detail.WalletDetailModel;
import org.multibit.hd.ui.views.components.wallet_detail.WalletDetailView;
import org.multibit.hd.ui.views.fonts.AwesomeIcon;
import org.multibit.hd.ui.views.wizards.AbstractWizard;
import org.multibit.hd.ui.views.wizards.AbstractWizardPanelView;

import javax.swing.*;

/**
 * <p>Wizard to provide the following to UI:</p>
 * <ul>
 * <li>Wallet details: Show</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public class WalletDetailPanelView extends AbstractWizardPanelView<WalletDetailWizardModel, String> {

  // View components
  private ModelAndView<WalletDetailModel, WalletDetailView> walletDetailMaV;

  /**
   * @param wizard    The wizard managing the states
   * @param panelName The panel name to allow event filtering
   */
  public WalletDetailPanelView(AbstractWizard<WalletDetailWizardModel> wizard, String panelName) {

    super(wizard, panelName, MessageKey.WALLET_DETAIL_TITLE, AwesomeIcon.INFO_CIRCLE);

  }

  @Override
  public void newPanelModel() {

    setPanelModel("");

    // No wizard model
  }

  @Override
  public void initialiseContent(JPanel contentPanel) {

    walletDetailMaV = Components.newWalletDetailMaV(getPanelName());

    contentPanel.setLayout(new MigLayout(
      Panels.migXYLayout(),
      "[]", // Column constraints
      "[]" // Row constraints
    ));

    contentPanel.add(walletDetailMaV.getView().newComponentPanel());

  }

  @Override
  protected void initialiseButtons(AbstractWizard<WalletDetailWizardModel> wizard) {

    PanelDecorator.addFinish(this, wizard);

  }

  @Override
  public void afterShow() {

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        getFinishButton().requestFocusInWindow();
      }
    });

  }

  @Override
  public void updateFromComponentModels(Optional componentModel) {
    // Do nothing - panel model is updated via an action and wizard model is not applicable
  }

}