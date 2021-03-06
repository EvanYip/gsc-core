package org.gsc.services.http;

        import com.alibaba.fastjson.JSONObject;
        import com.google.common.base.Strings;
        import com.google.protobuf.ByteString;
        import java.io.IOException;
        import java.util.stream.Collectors;
        import javax.servlet.http.HttpServlet;
        import javax.servlet.http.HttpServletRequest;
        import javax.servlet.http.HttpServletResponse;
        import lombok.extern.slf4j.Slf4j;
        import org.apache.commons.lang3.ArrayUtils;
        import org.gsc.common.utils.ByteArray;
        import org.gsc.core.Wallet;
        import org.gsc.protos.Contract;
        import org.gsc.protos.Protocol;
        import org.springframework.beans.factory.annotation.Autowired;
        import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeployContractServlet extends HttpServlet {

    @Autowired
    private Wallet wallet;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        try {
            String contract = request.getReader().lines()
                    .collect(Collectors.joining(System.lineSeparator()));
            Contract.CreateSmartContract.Builder build = Contract.CreateSmartContract.newBuilder();
            JSONObject jsonObject = JSONObject.parseObject(contract);
            byte[] ownerAddress = ByteArray.fromHexString(jsonObject.getString("owner_address"));
            build.setOwnerAddress(ByteString.copyFrom(ownerAddress));

            String abi = jsonObject.getString("abi");
            StringBuffer abiSB = new StringBuffer("{");
            abiSB.append("\"entrys\":");
            abiSB.append(abi);
            abiSB.append("}");
            Protocol.SmartContract.ABI.Builder abiBuilder = Protocol.SmartContract.ABI.newBuilder();
            JsonFormat.merge(abiSB.toString(), abiBuilder);

            long feeLimit = jsonObject.getLongValue("fee_limit");

            Protocol.SmartContract.Builder smartBuilder = Protocol.SmartContract.newBuilder();
            smartBuilder.setAbi(abiBuilder)
                    .setCallValue(jsonObject.getLongValue("call_value"))
                    .setConsumeUserResourcePercent(jsonObject.getLongValue("consume_user_resource_percent"));
            if (!ArrayUtils.isEmpty(ownerAddress)) {
                smartBuilder.setOriginAddress(ByteString.copyFrom(ownerAddress));
            }

            String jsonByteCode = jsonObject.getString("bytecode");
            if (jsonObject.containsKey("parameter")) {
                jsonByteCode += jsonObject.getString("parameter");
            }
            byte[] byteCode = ByteArray.fromHexString(jsonByteCode);
            if (!ArrayUtils.isEmpty(byteCode)) {
                smartBuilder.setBytecode(ByteString.copyFrom(byteCode));
            }
            String name = jsonObject.getString("name");
            if (!Strings.isNullOrEmpty(name)) {
                smartBuilder.setName(name);
            }

            build.setNewContract(smartBuilder);
            Protocol.Transaction tx = wallet
                    .createTransactionCapsule(build.build(), Protocol.Transaction.Contract.ContractType.CreateSmartContract).getInstance();
            Protocol.Transaction.Builder txBuilder = tx.toBuilder();
            Protocol.Transaction.raw.Builder rawBuilder = tx.getRawData().toBuilder();
            rawBuilder.setFeeLimit(feeLimit);
            txBuilder.setRawData(rawBuilder);
            response.getWriter().println(Util.printTransaction(txBuilder.build()));
        } catch (Exception e) {
            logger.debug("Exception: {}", e.getMessage());
            try {
                response.getWriter().println(Util.printErrorMsg(e));
            } catch (IOException ioe) {
                logger.debug("IOException: {}", ioe.getMessage());
            }
        }
    }
}