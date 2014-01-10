package org.multibit.hd.core.services;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.Before;
import org.junit.Test;
import org.multibit.hd.core.api.WalletData;
import org.multibit.hd.core.api.WalletIdTest;
import org.multibit.hd.core.api.seed_phrase.Bip39SeedPhraseGenerator;
import org.multibit.hd.core.api.seed_phrase.SeedPhraseGenerator;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.events.BitcoinSentEvent;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.managers.WalletManagerTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.fest.assertions.api.Assertions.assertThat;

public class BitcoinNetworkServiceFunctionalTest {

  private static final String WALLET_PASSWORD = "orinocoFlow";

  private static final String WALLET_SEED_1 = "walletSeed1";
  private static final String WALLET_SEED_2 = "walletSeed2";

  private static final BigInteger SEND_AMOUNT = BigInteger.valueOf(100000); // 0.001 BTC
  private static final BigInteger FEE_PER_KB = BigInteger.valueOf(10000); // 0.0001 BTC / KB

  private Properties seedProperties;

  private BitcoinNetworkService bitcoinNetworkService;

  private BitcoinSentEvent bitcoinSentEvent;

  private WalletManager walletManager;

  private static final Logger log = LoggerFactory.getLogger(BitcoinNetworkServiceFunctionalTest.class);

  @Before
  public void setUp() throws IOException {
    CoreServices.main(null);

    CoreServices.uiEventBus.register(this);

    walletManager = WalletManager.INSTANCE;

    // Create two wallets based on different seeds.
    // The seeds are stored in a file in resources called seed.properties.
    // This SHOULD NOT be under source control.
    //
    // A template file is provided called seed.properties.template for you to copy.
    seedProperties = loadWalletSeeds();
    assertThat(seedProperties).isNotNull();
    assertThat(seedProperties.getProperty(WALLET_SEED_1).length() > 0).isTrue();
    assertThat(seedProperties.getProperty(WALLET_SEED_2).length() > 0).isTrue();

    // You also need to fund one (or both)of the wallets so that there is some bitcoin to send from one to the other.
  }


  // This test is a simpler version of the testSendBetweenTwoRealWallets one
  // As they take a while to run if you are running the send test you don't really need to run this one
//  @Test
//  public void testSyncSingleWallet() throws Exception {
//    // Create a random temporary directory and use it for wallet storage
//    File temporaryDirectory = WalletManagerTest.makeRandomTemporaryDirectory();
//    walletManager.initialise(temporaryDirectory);
//
//    // Create a wallet from the WALLET_SEED_1
//    SeedPhraseGenerator seedGenerator = new Bip39SeedPhraseGenerator();
//    byte[] seed = seedGenerator.convertToSeed(WalletIdTest.split(seedProperties.getProperty(WALLET_SEED_1)));
//
//    Wallet wallet1 = createWallet(temporaryDirectory, seed, WALLET_PASSWORD);
//
//    synchronizeWallet();
//
//    log.debug("Wallet 1 (after sync) = \n" + wallet1.toString());
//
//    BigInteger walletBalance1 = wallet1.getBalance(Wallet.BalanceType.ESTIMATED);
//
//    // If this test fails please fund the test wallet with bitcoin as it is needed for the sending test !
//    // (The wallet is logged to the console so you can see the address you need to fund).
//    assertThat(walletBalance1.compareTo(BigInteger.ZERO) > 0).isTrue();
//  }

  @Test
  public void testSendBetweenTwoRealWallets() throws Exception {
    // Create a random temporary directory to store the wallets
    File temporaryDirectory = WalletManagerTest.makeRandomTemporaryDirectory();
    walletManager.initialise(temporaryDirectory);

    // Create two wallets from the two seeds
    SeedPhraseGenerator seedGenerator = new Bip39SeedPhraseGenerator();
    byte[] seed1 = seedGenerator.convertToSeed(WalletIdTest.split(seedProperties.getProperty(WALLET_SEED_1)));
    WalletData walletData1 = createWallet(temporaryDirectory, seed1);
    String walletRoot1 = walletManager.createWalletRoot(walletData1.getWalletId());


    byte[] seed2 = seedGenerator.convertToSeed(WalletIdTest.split(seedProperties.getProperty(WALLET_SEED_2)));
    WalletData walletData2 = createWallet(temporaryDirectory, seed2);
    String walletRoot2 = walletManager.createWalletRoot(walletData2.getWalletId());

    // Remember the addresses in the wallets, which will be used for the send
    ECKey key1 = walletData1.getWallet().getKeys().get(0);
    Address address1 = key1.toAddress(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));

    ECKey key2 = walletData2.getWallet().getKeys().get(0);
    Address address2 = key2.toAddress(NetworkParameters.fromID(NetworkParameters.ID_MAINNET));

    // Synchronize the current wallet, which will be wallet2 as that was created last
    synchronizeWallet();

    BigInteger walletBalance2 = walletData2.getWallet().getBalance();

    // Set the current wallet to be wallet1 and synchronize that
    Configurations.currentConfiguration.getApplicationConfiguration().setCurrentWalletRoot(walletRoot1);
    walletManager.setCurrentWalletData(walletData1);
    synchronizeWallet();

    BigInteger walletBalance1 = walletData1.getWallet().getBalance();

    log.debug("Wallet data 1 = \n" + walletData1.toString());
    log.debug("Wallet data 2 = \n" + walletData2.toString());


    // TODO Check any previous sends (probably sent by this test) have confirmed ok

    // Create a send from the wallet with the larger balance to the one with the smaller balance
    boolean sendFromWallet1 = walletBalance1.compareTo(walletBalance2) > 0;

    WalletData sourceWalletData;
    String walletRoot;
    Address sourceAddress;
    Address destinationAddress;
    if (sendFromWallet1) {
      sourceWalletData = walletData1;
      walletRoot = walletRoot1;
      sourceAddress = address1;
      destinationAddress = address2;
    } else {
      sourceWalletData = walletData2;
      walletRoot = walletRoot2;
      sourceAddress = address2;
      destinationAddress = address1;
    }

    // Ensure MBHD has the correct current wallet (which will be used for the send)
    Configurations.currentConfiguration.getApplicationConfiguration().setCurrentWalletRoot(walletRoot);
    walletManager.setCurrentWalletData(sourceWalletData);

    // Do the send
    bitcoinNetworkService = CoreServices.newBitcoinNetworkService();
    bitcoinNetworkService.start();

    // Wait a while for everything to start
    Uninterruptibles.sleepUninterruptibly(4000, TimeUnit.MILLISECONDS);

    try {
      // Clear the bitcoinSentEvent member variable so we know it is a new one later
      bitcoinSentEvent = null;

      // Send the bitcoins
      bitcoinNetworkService.send(destinationAddress.toString(), SEND_AMOUNT, sourceAddress.toString(), FEE_PER_KB, WALLET_PASSWORD);

      // the listenToBitcoinSentEvent method receives the bitcoinSentEvent once the send has completed
      Uninterruptibles.sleepUninterruptibly(4000, TimeUnit.MILLISECONDS);

      // Check for success
      assertThat(bitcoinSentEvent).isNotNull();
      assertThat(bitcoinSentEvent.isSendWasSuccessful()).isTrue();
    } finally {
      bitcoinNetworkService.stopAndWait();
    }
  }

  @Subscribe
  public void listenToBitcoinSentEvent(BitcoinSentEvent bitcoinSentEvent) {
    log.debug(bitcoinSentEvent.toString());
    this.bitcoinSentEvent = bitcoinSentEvent;
  }

  private WalletData createWallet(File walletDirectory, byte[] seed) throws IOException {
    WalletData walletData = walletManager.createWallet(walletDirectory.getAbsolutePath(), seed, WALLET_PASSWORD);
    assertThat(walletData).isNotNull();
    assertThat(walletData.getWallet()).isNotNull();

    // There should be a single key
    assertThat(walletData.getWallet().getKeychainSize() == 1).isTrue();

    return walletData;
  }

  private void synchronizeWallet() {
    bitcoinNetworkService = CoreServices.newBitcoinNetworkService();

    bitcoinNetworkService.start();
    bitcoinNetworkService.downloadBlockChain();

    // Wait until blockchain is downloaded
    // TODO monitor progress rather than absolute time
    //Uninterruptibles.sleepUninterruptibly(90, TimeUnit.SECONDS);

    bitcoinNetworkService.stopAndWait();
  }

  /**
   * Load the wallet seeds, which are stored in a properties file called seed.properties
   * The keys are specified by the WALLET_SEED_1 and WALLET_SEED_2 constants
   *
   * @return Properties containing wallet seeds
   */
  public static Properties loadWalletSeeds() throws FileNotFoundException, IOException {
    Properties seedProperties = new Properties();
    Class thisClass = BitcoinNetworkServiceFunctionalTest.class;

    InputStream inputStream = thisClass.getResourceAsStream("seed.properties");

    InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF8");
    seedProperties.load(inputStreamReader);
    log.debug("seedProperties = '" + seedProperties.toString());

    return seedProperties;
  }
}
