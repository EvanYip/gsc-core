package org.gsc.net.node;

import com.google.common.cache.Cache;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.gsc.common.application.GSCApplicationContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.gsc.common.application.Application;
import org.gsc.common.application.ApplicationFactory;
import org.gsc.common.overlay.client.PeerClient;
import org.gsc.common.overlay.discover.node.Node;
import org.gsc.common.overlay.server.Channel;
import org.gsc.common.overlay.server.ChannelManager;
import org.gsc.common.overlay.server.SyncPool;
import org.gsc.common.utils.FileUtil;
import org.gsc.common.utils.ReflectUtils;
import org.gsc.common.utils.Sha256Hash;
import org.gsc.core.wrapper.BlockWrapper;
import org.gsc.config.DefaultConfig;
import org.gsc.config.args.Args;
import org.gsc.db.ByteArrayWrapper;
import org.gsc.db.Manager;
import org.gsc.net.message.BlockMessage;
import org.gsc.net.peer.PeerConnection;
import org.gsc.services.RpcApiService;
import org.gsc.services.WitnessService;
import org.gsc.protos.Protocol;

@Slf4j
public class StartFetchSyncBlockTest {

  private static GSCApplicationContext context;
  private NodeImpl node;
  RpcApiService rpcApiService;
  PeerClient peerClient;
  ChannelManager channelManager;
  SyncPool pool;
  Application appT;
  private static final String dbPath = "output-nodeImplTest/startFetchSyncBlockTest";
  private static final String dbDirectory = "db_StartFetchSyncBlock_test";
  private static final String indexDirectory = "index_StartFetchSyncBlock_test";

  private class Condition {

    private Sha256Hash blockId;

    public Condition(Sha256Hash blockId) {
      this.blockId = blockId;
    }

    public Sha256Hash getBlockId() {
      return blockId;
    }

  }

  private Sha256Hash testBlockBroad() {
    Protocol.Block block = Protocol.Block.getDefaultInstance();
    BlockMessage blockMessage = new BlockMessage(new BlockWrapper(block));
    node.broadcast(blockMessage);
    ConcurrentHashMap<Sha256Hash, Protocol.Inventory.InventoryType> advObjToSpread = ReflectUtils
        .getFieldValue(node, "advObjToSpread");
    Assert.assertEquals(advObjToSpread.get(blockMessage.getMessageId()),
        Protocol.Inventory.InventoryType.BLOCK);
    return blockMessage.getMessageId();
  }

  private BlockMessage removeTheBlock(Sha256Hash blockId) {
    Cache<Sha256Hash, BlockMessage> blockCache = ReflectUtils.getFieldValue(node, "BlockCache");
    BlockMessage blockMessage = blockCache.getIfPresent(blockId);
    if (blockMessage != null) {
      blockCache.invalidate(blockId);
    }
    return blockMessage;
  }

  private void addTheBlock(BlockMessage blockMessag) {
    Cache<Sha256Hash, BlockMessage> blockCache = ReflectUtils.getFieldValue(node, "BlockCache");
    blockCache.put(blockMessag.getMessageId(), blockMessag);
  }

  private Condition testConsumerAdvObjToSpread() {
    Sha256Hash blockId = testBlockBroad();
    //remove the block
    BlockMessage blockMessage = removeTheBlock(blockId);
    ReflectUtils.invokeMethod(node, "consumerAdvObjToSpread");
    Collection<PeerConnection> activePeers = ReflectUtils.invokeMethod(node, "getActivePeer");

    boolean result = true;
    for (PeerConnection peerConnection : activePeers) {
      if (!peerConnection.getAdvObjWeSpread().containsKey(blockId)) {
        result &= false;
      }
    }
    for (PeerConnection peerConnection : activePeers) {
      peerConnection.getAdvObjWeSpread().clear();
    }
    Assert.assertTrue(result);
    return new Condition(blockId);
  }

  @Test
  public void testStartFetchSyncBlock() throws InterruptedException {
    testConsumerAdvObjToSpread();
    Collection<PeerConnection> activePeers = ReflectUtils.invokeMethod(node, "getActivePeer");
    Thread.sleep(1000);
    ReflectUtils.setFieldValue(activePeers.iterator().next(), "needSyncFromPeer", true);
    // construct a block
    Protocol.Block block = Protocol.Block.getDefaultInstance();
    BlockMessage blockMessage = new BlockMessage(new BlockWrapper(block));
    // push the block to syncBlockToFetch
    activePeers.iterator().next().getSyncBlockToFetch().push(blockMessage.getBlockId());
    // invoke testing method
    addTheBlock(blockMessage);
    ReflectUtils.invokeMethod(node, "startFetchSyncBlock");
    Cache syncBlockIdWeRequested = ReflectUtils
        .getFieldValue(node, "syncBlockIdWeRequested");
    Assert.assertTrue(syncBlockIdWeRequested.size() == 1);
  }


  private static boolean go = false;

  @Before
  public void init() {
    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        logger.info("Full node running.");
        Args.setParam(
            new String[]{
                "--output-directory", dbPath,
                "--storage-db-directory", dbDirectory,
                "--storage-index-directory", indexDirectory
            },
            "config.conf"
        );
        Args cfgArgs = Args.getInstance();
        cfgArgs.setNodeListenPort(17890);
        cfgArgs.setNodeDiscoveryEnable(false);
        cfgArgs.getSeedNode().getIpList().clear();
        cfgArgs.setNeedSyncCheck(false);
        cfgArgs.setNodeExternalIp("127.0.0.1");

        context = new GSCApplicationContext(DefaultConfig.class);

        if (cfgArgs.isHelp()) {
          logger.info("Here is the help message.");
          return;
        }
        appT = ApplicationFactory.create(context);
        rpcApiService = context.getBean(RpcApiService.class);
        appT.addService(rpcApiService);
        if (cfgArgs.isWitness()) {
          appT.addService(new WitnessService(appT, context));
        }
//        appT.initServices(cfgArgs);
//        appT.startServices();
//        appT.startup();
        node = context.getBean(NodeImpl.class);
        peerClient = context.getBean(PeerClient.class);
        channelManager = context.getBean(ChannelManager.class);
        pool = context.getBean(SyncPool.class);
        Manager dbManager = context.getBean(Manager.class);
        NodeDelegate nodeDelegate = new NodeDelegateImpl(dbManager);
        node.setNodeDelegate(nodeDelegate);
        pool.init(node);
        prepare();
        rpcApiService.blockUntilShutdown();
      }
    });
    thread.start();
    try {
      thread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    int tryTimes = 0;
    while (tryTimes < 10 && (node == null || peerClient == null
        || channelManager == null || pool == null || !go)) {
      try {
        logger.info("node:{},peerClient:{},channelManager:{},pool:{},{}", node, peerClient,
            channelManager, pool, go);
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      } finally {
        ++tryTimes;
      }
    }
  }

  private void prepare() {
    try {
      ExecutorService advertiseLoopThread = ReflectUtils.getFieldValue(node, "broadPool");
      advertiseLoopThread.shutdownNow();

      ReflectUtils.setFieldValue(node, "isAdvertiseActive", false);
      ReflectUtils.setFieldValue(node, "isFetchActive", false);

      Node node = new Node(
          "enode://e437a4836b77ad9d9ffe73ee782ef2614e6d8370fcf62191a6e488276e23717147073a7ce0b444d485fff5a0c34c4577251a7a990cf80d8542e21b95aa8c5e6c@127.0.0.1:17890");
      new Thread(new Runnable() {
        @Override
        public void run() {
          peerClient.connect(node.getHost(), node.getPort(), node.getHexId());
        }
      }).start();
      Thread.sleep(1000);
      Map<ByteArrayWrapper, Channel> activePeers = ReflectUtils
          .getFieldValue(channelManager, "activePeers");
      int tryTimes = 0;
      while (MapUtils.isEmpty(activePeers) && ++tryTimes < 10) {
        Thread.sleep(1000);
      }
      go = true;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @After
  public void destroy() {
    Args.clearParam();
    FileUtil.deleteDir(new File("output-nodeImplTest"));
    Collection<PeerConnection> peerConnections = ReflectUtils.invokeMethod(node, "getActivePeer");
    for (PeerConnection peer : peerConnections) {
      peer.close();
    }
    peerClient.close();
    appT.shutdownServices();
    appT.shutdown();
  }
}
