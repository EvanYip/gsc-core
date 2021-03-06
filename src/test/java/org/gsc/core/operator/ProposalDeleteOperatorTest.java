package org.gsc.core.operator;

import static junit.framework.TestCase.fail;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import java.io.File;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.gsc.common.application.GSCApplicationContext;
import org.gsc.config.DefaultConfig;
import org.gsc.config.args.Args;
import org.gsc.core.wrapper.*;
import org.gsc.core.wrapper.TransactionResultWrapper;
import org.gsc.core.wrapper.WitnessWrapper;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.gsc.common.utils.ByteArray;
import org.gsc.common.utils.FileUtil;
import org.gsc.common.utils.StringUtil;
import org.gsc.core.Constant;
import org.gsc.core.Wallet;
import org.gsc.db.Manager;
import org.gsc.core.exception.ContractExeException;
import org.gsc.core.exception.ContractValidateException;
import org.gsc.core.exception.ItemNotFoundException;
import org.gsc.protos.Contract;
import org.gsc.protos.Protocol.AccountType;
import org.gsc.protos.Protocol.Proposal.State;
import org.gsc.protos.Protocol.Transaction.Result.code;

@Slf4j

public class ProposalDeleteOperatorTest {

  private static GSCApplicationContext context;
  private static Manager dbManager;
  private static final String dbPath = "output_ProposalApprove_test";
  private static final String ACCOUNT_NAME_FIRST = "ownerF";
  private static final String OWNER_ADDRESS_FIRST;
  private static final String ACCOUNT_NAME_SECOND = "ownerS";
  private static final String OWNER_ADDRESS_SECOND;
  private static final String URL = "https://gscan.social";
  private static final String OWNER_ADDRESS_INVALID = "aaaa";
  private static final String OWNER_ADDRESS_NOACCOUNT;

  static {
    Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
    context = new GSCApplicationContext(DefaultConfig.class);
    OWNER_ADDRESS_FIRST =
        Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049abc";
    OWNER_ADDRESS_SECOND =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1abc";
    OWNER_ADDRESS_NOACCOUNT =
        Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a1aed";
  }

  /**
   * Init data.
   */
  @BeforeClass
  public static void init() {
    dbManager = context.getBean(Manager.class);
  }

  /**
   * Release resources.
   */
  @AfterClass
  public static void destroy() {
    Args.clearParam();
    if (FileUtil.deleteDir(new File(dbPath))) {
      logger.info("Release resources successful.");
    } else {
      logger.info("Release resources failure.");
    }
    context.destroy();
  }

  /**
   * create temp Capsule test need.
   */
  @Before
  public void initTest() {
    WitnessWrapper ownerWitnessFirstCapsule =
        new WitnessWrapper(
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
            10_000_000L,
            URL);
    AccountWrapper ownerAccountFirstCapsule =
        new AccountWrapper(
            ByteString.copyFromUtf8(ACCOUNT_NAME_FIRST),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_FIRST)),
            AccountType.Normal,
            300_000_000L);
    AccountWrapper ownerAccountSecondCapsule =
        new AccountWrapper(
            ByteString.copyFromUtf8(ACCOUNT_NAME_SECOND),
            ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND)),
            AccountType.Normal,
            200_000_000_000L);

    dbManager.getAccountStore()
        .put(ownerAccountFirstCapsule.getAddress().toByteArray(), ownerAccountFirstCapsule);
    dbManager.getAccountStore()
        .put(ownerAccountSecondCapsule.getAddress().toByteArray(), ownerAccountSecondCapsule);

    dbManager.getWitnessStore().put(ownerWitnessFirstCapsule.getAddress().toByteArray(),
        ownerWitnessFirstCapsule);

    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000000);
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderNumber(10);
    dbManager.getDynamicPropertiesStore().saveNextMaintenanceTime(2000000);
    dbManager.getDynamicPropertiesStore().saveLatestProposalNum(0);

    long id = 1;
    dbManager.getProposalStore().delete(ByteArray.fromLong(1));
    dbManager.getProposalStore().delete(ByteArray.fromLong(2));
    HashMap<Long, Long> paras = new HashMap<>();
    paras.put(0L, 3 * 27 * 1000L);
    ProposalCreateOperator actuator =
        new ProposalCreateOperator(getContract(OWNER_ADDRESS_FIRST, paras), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 0);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      ProposalWrapper proposalWrapper = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      Assert.assertNotNull(proposalWrapper);
      Assert.assertEquals(dbManager.getDynamicPropertiesStore().getLatestProposalNum(), 1);
      Assert.assertEquals(proposalWrapper.getApprovals().size(), 0);
      Assert.assertEquals(proposalWrapper.getCreateTime(), 1000000);
      Assert.assertEquals(proposalWrapper.getExpirationTime(),
          261200000); // 2000000 + 3 * 4 * 21600000
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    } catch (ItemNotFoundException e) {
      Assert.assertFalse(e instanceof ItemNotFoundException);
    }
  }

  private Any getContract(String address, HashMap<Long, Long> paras) {
    return Any.pack(
        Contract.ProposalCreateContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .putAllParameters(paras)
            .build());
  }

  private Any getContract(String address, long id) {
    return Any.pack(
        Contract.ProposalDeleteContract.newBuilder()
            .setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(address)))
            .setProposalId(id)
            .build());
  }

  /**
   * first deleteProposal, result is success.
   */
  @Test
  public void successDeleteApprove() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
    long id = 1;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_FIRST, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    ProposalWrapper proposalWrapper;
    try {
      proposalWrapper = dbManager.getProposalStore().get(ByteArray.fromLong(id));
    } catch (ItemNotFoundException e) {
      Assert.assertFalse(e instanceof ItemNotFoundException);
      return;
    }
    Assert.assertEquals(proposalWrapper.getState(), State.PENDING);
    try {
      actuator.validate();
      actuator.execute(ret);
      Assert.assertEquals(ret.getInstance().getRet(), code.SUCESS);
      try {
        proposalWrapper = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      } catch (ItemNotFoundException e) {
        Assert.assertFalse(e instanceof ItemNotFoundException);
        return;
      }
      Assert.assertEquals(proposalWrapper.getState(), State.CANCELED);
    } catch (ContractValidateException e) {
      Assert.assertFalse(e instanceof ContractValidateException);
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }

  }

  /**
   * use Invalid Address, result is failed, exception is "Invalid address".
   */
  @Test
  public void invalidAddress() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
    long id = 1;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_INVALID, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Invalid address");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Invalid address", e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Account not exists, result is failed, exception is "account not exists".
   */
  @Test
  public void noAccount() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
    long id = 1;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_NOACCOUNT, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("account[+OWNER_ADDRESS_NOACCOUNT+] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("account[" + OWNER_ADDRESS_NOACCOUNT + "] not exists",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Proposal is not proposed by witness, result is failed,exception is "witness not exists".
   */
  @Test
  public void notProposed() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
    long id = 1;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_SECOND, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("witness[+OWNER_ADDRESS_NOWITNESS+] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Proposal[" + id + "] " + "is not proposed by "
              + StringUtil.createReadableString(
          ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS_SECOND))),
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * use Proposal not exists, result is failed, exception is "Proposal not exists".
   */
  @Test
  public void noProposal() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(1000100);
    long id = 2;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_FIRST, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Proposal[" + id + "] not exists");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Proposal[" + id + "] not exists",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Proposal expired, result is failed, exception is "Proposal expired".
   */
  @Test
  public void proposalExpired() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(261200100);
    long id = 1;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_FIRST, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Proposal[" + id + "] expired");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Proposal[" + id + "] expired",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

  /**
   * Proposal canceled, result is failed, exception is "Proposal expired".
   */
  @Test
  public void proposalCanceled() {
    dbManager.getDynamicPropertiesStore().saveLatestBlockHeaderTimestamp(100100);
    long id = 1;

    ProposalDeleteOperator actuator = new ProposalDeleteOperator(
        getContract(OWNER_ADDRESS_FIRST, id), dbManager);
    TransactionResultWrapper ret = new TransactionResultWrapper();
    ProposalWrapper proposalWrapper;
    try {
      proposalWrapper = dbManager.getProposalStore().get(ByteArray.fromLong(id));
      proposalWrapper.setState(State.CANCELED);
      dbManager.getProposalStore().put(proposalWrapper.createDbKey(), proposalWrapper);
    } catch (ItemNotFoundException e) {
      Assert.assertFalse(e instanceof ItemNotFoundException);
      return;
    }
    Assert.assertEquals(proposalWrapper.getApprovals().size(), 0);
    try {
      actuator.validate();
      actuator.execute(ret);
      fail("Proposal[" + id + "] canceled");
    } catch (ContractValidateException e) {
      Assert.assertTrue(e instanceof ContractValidateException);
      Assert.assertEquals("Proposal[" + id + "] canceled",
          e.getMessage());
    } catch (ContractExeException e) {
      Assert.assertFalse(e instanceof ContractExeException);
    }
  }

}