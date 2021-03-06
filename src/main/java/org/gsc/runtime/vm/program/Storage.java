package org.gsc.runtime.vm.program;

import static java.lang.System.arraycopy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.gsc.core.wrapper.StorageRowWrapper;
import org.gsc.crypto.Hash;
import org.gsc.runtime.vm.DataWord;
import org.gsc.db.StorageRowStore;

public class Storage {

  private byte[] addrHash;  // contract address
  private StorageRowStore store;
  private final Map<DataWord, StorageRowWrapper> rowCache = new HashMap<>();
  private long beforeUseSize = 0;

  private static final int PREFIX_BYTES = 16;

  public Storage(byte[] address, StorageRowStore store) {
    addrHash = addrHash(address);
    this.store = store;
  }

  public DataWord getValue(DataWord key) {
    if (rowCache.containsKey(key)) {
      return rowCache.get(key).getValue();
    } else {
      StorageRowWrapper row = store.get(compose(key.getData(), addrHash));
      if (row == null || row.getInstance() == null) {
        return null;
      } else {
        beforeUseSize += row.getInstance().length;
      }
      rowCache.put(key, row);
      return row.getValue();
    }
  }

  public void put(DataWord key, DataWord value) {
    if (rowCache.containsKey(key)) {
      rowCache.get(key).setValue(value);
    } else {
      byte[] rowKey = compose(key.getData(), addrHash);
      StorageRowWrapper row = store.get(rowKey);
      if (row == null || row.getInstance() == null) {
        row = new StorageRowWrapper(rowKey, value.getData());
      } else {
        row.setValue(value);
        beforeUseSize += row.getInstance().length;
      }
      rowCache.put(key, row);
    }
  }

  private static byte[] compose(byte[] key, byte[] addrHash) {
    byte[] result = new byte[key.length];
    arraycopy(addrHash, 0, result, 0, PREFIX_BYTES);
    arraycopy(key, PREFIX_BYTES, result, PREFIX_BYTES, PREFIX_BYTES);
    return result;
  }

  // 32 bytes
  private static byte[] addrHash(byte[] address) {
    return Hash.sha3(address);
  }

  public long computeSize() {
    AtomicLong size = new AtomicLong();
    rowCache.forEach((key, value) -> {
      if (!value.getValue().isZero()) {
        size.getAndAdd(value.getInstance().length);
      }
    });
    return size.get();
  }

  public long getBeforeUseSize() {
    return this.beforeUseSize;
  }

  public void commit() {
    rowCache.forEach((key, value) -> {
      if (value.isDirty()) {
        if (value.getValue().isZero()) {
          this.store.delete(value.getRowKey());
        } else {
          this.store.put(value.getRowKey(), value);
        }
      }
    });
  }
}
