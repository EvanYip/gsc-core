package org.gsc.config.args;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import io.grpc.internal.GrpcUtil;
import io.grpc.netty.NettyServerBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gsc.crypto.ECKey;
import org.gsc.common.overlay.discover.node.Node;
import org.gsc.common.utils.ByteArray;
import org.gsc.core.Constant;
import org.gsc.keystore.CipherException;
import org.gsc.keystore.Credentials;
import org.gsc.keystore.WalletUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.stereotype.Component;
import org.gsc.core.Wallet;
import org.gsc.config.Configuration;
import org.gsc.config.Parameter.ChainConstant;
import org.gsc.db.AccountStore;

@Slf4j
@NoArgsConstructor
@Component
public class Args {

  private static final Args INSTANCE = new Args();

  @Parameter(names = {"-c", "--config"}, description = "Config File")
  private String shellConfFileName = "";

  @Parameter(names = {"-d", "--output-directory"}, description = "Directory")
  private String outputDirectory = "output-directory";

  @Getter
  @Parameter(names = {"-h", "--help"}, help = true, description = "HELP message")
  private boolean help = false;

  @Getter
  @Setter
  @Parameter(names = {"-w", "--witness"})
  private boolean witness = false;

  @Getter
  @Parameter(description = "--seed-peer")
  private List<String> seedNodes = new ArrayList<>();

  @Parameter(names = {"-p", "--private-key"}, description = "private-key")
  private String privateKey = "";

  @Parameter(names = {"--password"}, description = "password")
  private String password;

  @Parameter(names = {"--storage-db-directory"}, description = "Storage db directory")
  private String storageDbDirectory = "";

  @Parameter(names = {"--storage-index-directory"}, description = "Storage index directory")
  private String storageIndexDirectory = "";

  @Getter
  private Storage storage;

  @Getter
  private Overlay overlay;

  @Getter
  private SeedNode seedNode;

  @Getter
  private GenesisBlock genesisBlock;

  @Getter
  @Setter
  private String chainId;

  @Getter
  @Setter
  private LocalWitnesses localWitnesses = new LocalWitnesses();

  @Getter
  @Setter
  private boolean needSyncCheck;

  @Getter
  @Setter
  private boolean nodeDiscoveryEnable;

  @Getter
  @Setter
  private boolean nodeDiscoveryPersist;

  @Getter
  @Setter
  private int nodeConnectionTimeout;

  @Getter
  @Setter
  private List<Node> activeNodes;

  @Getter
  @Setter
  private List<Node> passiveNodes;

  @Getter
  @Setter
  private int nodeChannelReadTimeout;

  @Getter
  @Setter
  private int nodeMaxActiveNodes;

  @Getter
  @Setter
  private int nodeMaxActiveNodesWithSameIp;

  @Getter
  @Setter
  private int minParticipationRate;

  @Getter
  @Setter
  private int nodeListenPort;

  @Getter
  @Setter
  private String nodeDiscoveryBindIp;

  @Getter
  @Setter
  private String nodeExternalIp;

  @Getter
  @Setter
  private boolean nodeDiscoveryPublicHomeNode;

  @Getter
  @Setter
  private long nodeP2pPingInterval;

//  @Getter
//  @Setter
//  private long syncNodeCount;

  @Getter
  @Setter
  private int nodeP2pVersion;

  @Getter
  @Setter
  private String p2pNodeId;

  //If you are running a solidity node for java gsc, this flag is set to true
  @Getter
  @Setter
  private boolean solidityNode = false;

  @Getter
  @Setter
  private int rpcPort;

  @Getter
  @Setter
  @Parameter(names = {"--rpc-thread"}, description = "Num of gRPC thread")
  private int rpcThreadNum;

  @Getter
  @Setter
  private int maxConcurrentCallsPerConnection;

  @Getter
  @Setter
  private int flowControlWindow;

  @Getter
  @Setter
  private long maxConnectionIdleInMillis;

  @Getter
  @Setter
  private long maxConnectionAgeInMillis;

  @Getter
  @Setter
  private int maxMessageSize;

  @Getter
  @Setter
  private int maxHeaderListSize;

  @Getter
  @Setter
  @Parameter(names = {"--validate-sign-thread"}, description = "Num of validate thread")
  private int validateSignThreadNum;

  @Getter
  @Setter
  private long maintenanceTimeInterval; // (ms)

  @Getter
  @Setter
  private int tcpNettyWorkThreadNum;

  @Getter
  @Setter
  private int udpNettyWorkThreadNum;

  @Getter
  @Setter
  @Parameter(names = {"--trust-peer"}, description = "Trust node addr")
  private String trustNodeAddr;

  @Getter
  @Setter
  private boolean walletExtensionApi;

  @Getter
  @Setter
  private int backupPriority;

  @Getter
  @Setter
  private int backupPort;

  @Getter
  @Setter
  private List<String> backupMembers;

  public static void clearParam() {
    INSTANCE.outputDirectory = "output-directory";
    INSTANCE.help = false;
    INSTANCE.witness = false;
    INSTANCE.seedNodes = new ArrayList<>();
    INSTANCE.privateKey = "";
    INSTANCE.storageDbDirectory = "";
    INSTANCE.storageIndexDirectory = "";

    // FIXME: INSTANCE.storage maybe null ?
    if (INSTANCE.storage != null) {
      // WARNING: WILL DELETE DB STORAGE PATHS
      INSTANCE.storage.deleteAllStoragePaths();
      INSTANCE.storage = null;
    }

    INSTANCE.overlay = null;
    INSTANCE.seedNode = null;
    INSTANCE.genesisBlock = null;
    INSTANCE.chainId = null;
    INSTANCE.localWitnesses = null;
    INSTANCE.needSyncCheck = false;
    INSTANCE.nodeDiscoveryEnable = false;
    INSTANCE.nodeDiscoveryPersist = false;
    INSTANCE.nodeConnectionTimeout = 0;
    INSTANCE.activeNodes = Collections.emptyList();
    INSTANCE.passiveNodes = Collections.emptyList();
    INSTANCE.nodeChannelReadTimeout = 0;
    INSTANCE.nodeMaxActiveNodes = 30;
    INSTANCE.nodeMaxActiveNodesWithSameIp = 2;
    INSTANCE.minParticipationRate = 0;
    INSTANCE.nodeListenPort = 0;
    INSTANCE.nodeDiscoveryBindIp = "";
    INSTANCE.nodeExternalIp = "";
    INSTANCE.nodeDiscoveryPublicHomeNode = false;
    INSTANCE.nodeP2pPingInterval = 0L;
    //INSTANCE.syncNodeCount = 0;
    INSTANCE.nodeP2pVersion = 0;
    INSTANCE.rpcPort = 0;
    INSTANCE.maintenanceTimeInterval = 0;
    INSTANCE.tcpNettyWorkThreadNum = 0;
    INSTANCE.udpNettyWorkThreadNum = 0;
    INSTANCE.p2pNodeId = "";
    INSTANCE.solidityNode = false;
    INSTANCE.trustNodeAddr = "";
    INSTANCE.walletExtensionApi = false;
  }

  /**
   * set parameters.
   */
  public static void setParam(final String[] args, final String confFileName) {
    JCommander.newBuilder().addObject(INSTANCE).build().parse(args);
    Config config = Configuration.getByFileName(INSTANCE.shellConfFileName, confFileName);
    if (StringUtils.isNoneBlank(INSTANCE.privateKey)) {
      INSTANCE.setLocalWitnesses(new LocalWitnesses(INSTANCE.privateKey));
      logger.debug("Got privateKey from cmd");
    } else if (config.hasPath("localwitness")) {
      INSTANCE.localWitnesses = new LocalWitnesses();
      List<String> localwitness = config.getStringList("localwitness");
      if (localwitness.size() > 1) {
        logger.warn("localwitness size must be one, get the first one");
        localwitness = localwitness.subList(0, 1);
      }
      INSTANCE.localWitnesses.setPrivateKeys(localwitness);
      logger.debug("Got privateKey from config.conf");
    } else if (config.hasPath("localwitnesskeystore")) {
      INSTANCE.localWitnesses = new LocalWitnesses();
      List<String> privateKeys = new ArrayList<String>();
      if (INSTANCE.isWitness()) {
        List<String> localwitness = config.getStringList("localwitnesskeystore");
        if (localwitness.size() > 0) {
          String fileName = System.getProperty("user.dir") + "/" + localwitness.get(0);
          String password;
          if (StringUtils.isEmpty(INSTANCE.password)) {
            System.out.println("Please input your password.");
            password = WalletUtils.inputPassword();
          } else {
            password = INSTANCE.password;
            INSTANCE.password = null;
          }

          try {
            Credentials credentials = WalletUtils
                .loadCredentials(password, new File(fileName));
            ECKey ecKeyPair = credentials.getEcKeyPair();
            String prikey = ByteArray.toHexString(ecKeyPair.getPrivKeyBytes());
            privateKeys.add(prikey);
          } catch (IOException e) {
            logger.error(e.getMessage());
            logger.error("Witness node start faild!");
            System.exit(-1);
          } catch (CipherException e) {
            logger.error(e.getMessage());
            logger.error("Witness node start faild!");
            System.exit(-1);
          }
        }
      }
      INSTANCE.localWitnesses.setPrivateKeys(privateKeys);
      logger.debug("Got privateKey from keystore");
    }

    if (INSTANCE.isWitness() && CollectionUtils.isEmpty(INSTANCE.localWitnesses.getPrivateKeys())) {
      logger.warn("This is a witness node,but localWitnesses is null");
    }

    INSTANCE.storage = new Storage();
    INSTANCE.storage.setDbDirectory(Optional.ofNullable(INSTANCE.storageDbDirectory)
        .filter(StringUtils::isNotEmpty)
        .orElse(Storage.getDbDirectoryFromConfig(config)));

    INSTANCE.storage.setIndexDirectory(Optional.ofNullable(INSTANCE.storageIndexDirectory)
        .filter(StringUtils::isNotEmpty)
        .orElse(Storage.getIndexDirectoryFromConfig(config)));

    INSTANCE.storage.setPropertyMapFromConfig(config);

    INSTANCE.seedNode = new SeedNode();
    INSTANCE.seedNode.setIpList(Optional.ofNullable(INSTANCE.seedNodes)
        .filter(seedNode -> 0 != seedNode.size())
        .orElse(config.getStringList("seed.peer.ip.list")));

    if (config.hasPath("network.type") && "mainnet".equalsIgnoreCase(config.getString("net.type"))) {
      Wallet.setAddressPreFixByte(Constant.ADD_PRE_FIX_BYTE_MAINNET);
      Wallet.setAddressPreFixString(Constant.ADD_PRE_FIX_STRING_MAINNET);
    } else {
      Wallet.setAddressPreFixByte(Constant.ADD_PRE_FIX_BYTE_TESTNET);
      Wallet.setAddressPreFixString(Constant.ADD_PRE_FIX_STRING_TESTNET);
    }

    if (config.hasPath("genesis.block")) {
      INSTANCE.genesisBlock = new GenesisBlock();

      INSTANCE.genesisBlock.setTimestamp(config.getString("genesis.block.timestamp"));
      INSTANCE.genesisBlock.setParentHash(config.getString("genesis.block.parentHash"));

      if (config.hasPath("genesis.block.assets")) {
        INSTANCE.genesisBlock.setAssets(getAccountsFromConfig(config));
        AccountStore.setAccount(config);
      }
      if (config.hasPath("genesis.block.witnesses")) {
        INSTANCE.genesisBlock.setWitnesses(getWitnessesFromConfig(config));
      }
    } else {
      INSTANCE.genesisBlock = GenesisBlock.getDefault();
    }

    INSTANCE.needSyncCheck =
        config.hasPath("block.needSyncCheck") && config.getBoolean("block.needSyncCheck");

    INSTANCE.nodeDiscoveryEnable =
        config.hasPath("peer.discovery.enable") && config.getBoolean("peer.discovery.enable");

    INSTANCE.nodeDiscoveryPersist =
        config.hasPath("peer.discovery.persist") && config.getBoolean("peer.discovery.persist");

    INSTANCE.nodeConnectionTimeout =
        config.hasPath("peer.connection.timeout") ? config.getInt("node.connection.timeout") * 1000
            : 0;

    INSTANCE.activeNodes = getNodes(config, "peer.active");

    INSTANCE.passiveNodes = getNodes(config, "peer.passive");

    INSTANCE.nodeChannelReadTimeout =
        config.hasPath("peer.channel.read.timeout") ? config.getInt("peer.channel.read.timeout")
            : 0;

    INSTANCE.nodeMaxActiveNodes =
        config.hasPath("peer.maxActiveNodes") ? config.getInt("peer.maxActiveNodes") : 30;

    INSTANCE.nodeMaxActiveNodesWithSameIp =
        config.hasPath("peer.maxActiveNodesWithSameIp") ? config.getInt("peer.maxActiveNodesWithSameIp") : 2;

    INSTANCE.minParticipationRate =
        config.hasPath("peer.minParticipationRate") ? config.getInt("peer.minParticipationRate")
            : 0;

    INSTANCE.nodeListenPort =
        config.hasPath("peer.listen.port") ? config.getInt("peer.listen.port") : 0;

    bindIp(config);
    externalIp(config);

    INSTANCE.nodeDiscoveryPublicHomeNode =
        config.hasPath("peer.discovery.public.home.node") && config
            .getBoolean("peer.discovery.public.home.node");

    INSTANCE.nodeP2pPingInterval =
        config.hasPath("peer.p2p.pingInterval") ? config.getLong("peer.p2p.pingInterval") : 0;
//
//    INSTANCE.syncNodeCount =
//        config.hasPath("sync.peer.count") ? config.getLong("sync.peer.count") : 0;

    INSTANCE.nodeP2pVersion =
        config.hasPath("peer.p2p.version") ? config.getInt("peer.p2p.version") : 0;

    INSTANCE.rpcPort =
        config.hasPath("peer.rpc.port") ? config.getInt("peer.rpc.port") : 50051;

    INSTANCE.rpcThreadNum =
        config.hasPath("peer.rpc.thread") ? config.getInt("peer.rpc.thread")
            : Runtime.getRuntime().availableProcessors() / 2;

    INSTANCE.maxConcurrentCallsPerConnection =
        config.hasPath("peer.rpc.maxConcurrentCallsPerConnection") ?
            config.getInt("peer.rpc.maxConcurrentCallsPerConnection") : Integer.MAX_VALUE;

    INSTANCE.flowControlWindow = config.hasPath("node.rpc.flowControlWindow") ?
        config.getInt("peer.rpc.flowControlWindow")
        : NettyServerBuilder.DEFAULT_FLOW_CONTROL_WINDOW;

    INSTANCE.maxConnectionIdleInMillis = config.hasPath("peer.rpc.maxConnectionIdleInMillis") ?
        config.getLong("peer.rpc.maxConnectionIdleInMillis") : Long.MAX_VALUE;

    INSTANCE.maxConnectionAgeInMillis = config.hasPath("node.rpc.maxConnectionAgeInMillis") ?
        config.getLong("peer.rpc.maxConnectionAgeInMillis") : Long.MAX_VALUE;

    INSTANCE.maxMessageSize = config.hasPath("node.rpc.maxMessageSize") ?
        config.getInt("peer.rpc.maxMessageSize") : GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;

    INSTANCE.maxHeaderListSize = config.hasPath("node.rpc.maxHeaderListSize") ?
        config.getInt("peer.rpc.maxHeaderListSize") : GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE;

    INSTANCE.maintenanceTimeInterval =
        config.hasPath("block.maintenanceTimeInterval") ? config
            .getInt("block.maintenanceTimeInterval") : 21600000L;

    INSTANCE.tcpNettyWorkThreadNum = config.hasPath("peer.tcpNettyWorkThreadNum") ? config
        .getInt("peer.tcpNettyWorkThreadNum") : 0;

    INSTANCE.udpNettyWorkThreadNum = config.hasPath("peer.udpNettyWorkThreadNum") ? config
        .getInt("peer.udpNettyWorkThreadNum") : 1;

    if (StringUtils.isEmpty(INSTANCE.trustNodeAddr)) {
      INSTANCE.trustNodeAddr =
          config.hasPath("peer.trustPeer") ? config.getString("peer.trustPeer") : null;
    }

    INSTANCE.validateSignThreadNum = config.hasPath("peer.validateSignThreadNum") ? config
        .getInt("peer.validateSignThreadNum") : Runtime.getRuntime().availableProcessors() / 2;

    INSTANCE.walletExtensionApi =
        config.hasPath("peer.walletExtensionApi") && config.getBoolean("peer.walletExtensionApi");

    initBackupProperty(config);
  }


  private static List<Witness> getWitnessesFromConfig(final com.typesafe.config.Config config) {
    return config.getObjectList("genesis.block.witnesses").stream()
        .map(Args::createWitness)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static Witness createWitness(final ConfigObject witnessAccount) {
    final Witness witness = new Witness();
    witness.setAddress(
        Wallet.decodeFromBase58Check(witnessAccount.get("address").unwrapped().toString()));
    witness.setUrl(witnessAccount.get("url").unwrapped().toString());
    witness.setVoteCount(witnessAccount.toConfig().getLong("voteCount"));
    return witness;
  }

  private static List<Account> getAccountsFromConfig(final com.typesafe.config.Config config) {
    return config.getObjectList("genesis.block.assets").stream()
        .map(Args::createAccount)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private static Account createAccount(final ConfigObject asset) {
    final Account account = new Account();
    account.setAccountName(asset.get("accountName").unwrapped().toString());
    account.setAccountType(asset.get("accountType").unwrapped().toString());
    account.setAddress(Wallet.decodeFromBase58Check(asset.get("address").unwrapped().toString()));
    account.setBalance(asset.get("balance").unwrapped().toString());
    return account;
  }

  public static Args getInstance() {
    return INSTANCE;
  }

  /**
   * Get storage path by name of database
   *
   * @param dbName name of database
   * @return path of that database
   */
  public String getOutputDirectoryByDbName(String dbName) {
    String path = storage.getPathByDbName(dbName);
    if (!StringUtils.isBlank(path)) {
      return path;
    }
    return getOutputDirectory();
  }

  /**
   * get output directory.
   */
  public String getOutputDirectory() {
    if (!this.outputDirectory.equals("") && !this.outputDirectory.endsWith(File.separator)) {
      return this.outputDirectory + File.separator;
    }
    return this.outputDirectory;
  }

  private static List<Node> getNodes(final com.typesafe.config.Config config, String path) {
    if (!config.hasPath(path)) {
      return Collections.EMPTY_LIST;
    }
    List<Node> ret = new ArrayList<>();
    List<String> list = config.getStringList(path);
    for (String configString : list) {
      Node n = Node.instanceOf(configString);
      ret.add(n);
    }
    return ret;
  }

  private static void privateKey(final com.typesafe.config.Config config) {
    if (config.hasPath("private.key")) {
      INSTANCE.privateKey = config.getString("private.key");
      if (INSTANCE.privateKey.length() != ChainConstant.PRIVATE_KEY_LENGTH) {
        throw new RuntimeException(
            "The peer.privateKey needs to be Hex encoded and 32 byte length");
      }
    } else {
      INSTANCE.privateKey = getGeneratedNodePrivateKey();
    }
  }

  private static String getGeneratedNodePrivateKey() {
    String nodeId;
    try {
      File file = new File(
          INSTANCE.outputDirectory + File.separator + INSTANCE.storage.getDbDirectory(),
          "nodeId.properties");
      Properties props = new Properties();
      if (file.canRead()) {
        try (Reader r = new FileReader(file)) {
          props.load(r);
        }
      } else {
        ECKey key = new ECKey();
        props.setProperty("nodeIdPrivateKey", Hex.toHexString(key.getPrivKeyBytes()));
        props.setProperty("nodeId", Hex.toHexString(key.getNodeId()));
        file.getParentFile().mkdirs();
        try (Writer w = new FileWriter(file)) {
          props.store(w,
              "Generated NodeID. To use your own nodeId please refer to 'peer.privateKey' config option.");
        }
        logger.info("New nodeID generated: " + props.getProperty("nodeId"));
        logger.info("Generated nodeID and its private key stored in " + file);
      }
      nodeId = props.getProperty("nodeIdPrivateKey");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return nodeId;
  }

  private static void bindIp(final com.typesafe.config.Config config) {
    if (!config.hasPath("peer.discovery.bind.ip") || config.getString("peer.discovery.bind.ip")
        .trim().isEmpty()) {
      if (INSTANCE.nodeDiscoveryBindIp == null) {
        logger.info("Bind address wasn't set, Punching to identify it...");
        try (Socket s = new Socket("www.baidu.com", 80)) {
          INSTANCE.nodeDiscoveryBindIp = s.getLocalAddress().getHostAddress();
          logger.info("UDP local bound to: {}", INSTANCE.nodeDiscoveryBindIp);
        } catch (IOException e) {
          logger.warn("Can't get bind IP. Fall back to 0.0.0.0: " + e);
          INSTANCE.nodeDiscoveryBindIp = "0.0.0.0";
        }
      }
    } else {
      INSTANCE.nodeDiscoveryBindIp = config.getString("peer.discovery.bind.ip").trim();
    }
  }

  private static void externalIp(final com.typesafe.config.Config config) {
    if (!config.hasPath("peer.discovery.external.ip") || config
        .getString("peer.discovery.external.ip").trim().isEmpty()) {
      if (INSTANCE.nodeExternalIp == null) {
        logger.info("External IP wasn't set, using checkip.amazonaws.com to identify it...");
        BufferedReader in = null;
        try {
          in = new BufferedReader(new InputStreamReader(
              new URL("http://checkip.amazonaws.com").openStream()));
          INSTANCE.nodeExternalIp = in.readLine();
          if (INSTANCE.nodeExternalIp == null || INSTANCE.nodeExternalIp.trim().isEmpty()) {
            throw new IOException("Invalid address: '" + INSTANCE.nodeExternalIp + "'");
          }
          try {
            InetAddress.getByName(INSTANCE.nodeExternalIp);
          } catch (Exception e) {
            throw new IOException("Invalid address: '" + INSTANCE.nodeExternalIp + "'");
          }
          logger.info("External address identified: {}", INSTANCE.nodeExternalIp);
        } catch (IOException e) {
          INSTANCE.nodeExternalIp = INSTANCE.nodeDiscoveryBindIp;
          logger.warn(
              "Can't get external IP. Fall back to peer.bind.ip: " + INSTANCE.nodeExternalIp + " :"
                  + e);
        } finally {
          if (in != null) {
            try {
              in.close();
            } catch (IOException e) {
              //ignore
            }
          }

        }
      }
    } else {
      INSTANCE.nodeExternalIp = config.getString("peer.discovery.external.ip").trim();
    }
  }

  public ECKey getMyKey() {
    if (StringUtils.isEmpty(INSTANCE.p2pNodeId)) {
      INSTANCE.p2pNodeId = getGeneratedNodePrivateKey();
    }

    return ECKey.fromPrivate(Hex.decode(INSTANCE.p2pNodeId));
  }

  private static void initBackupProperty(Config config) {
    INSTANCE.backupPriority = config.hasPath("peer.discover.priority")
        ? config.getInt("peer.discover.priority") : 0;
    INSTANCE.backupPort = config.hasPath("peer.discover.port")
        ? config.getInt("peer.discover.port") : 10001;
    INSTANCE.backupMembers = config.hasPath("peer.discover.members")
        ? config.getStringList("peer.discover.members") : new ArrayList<>();
  }
}
