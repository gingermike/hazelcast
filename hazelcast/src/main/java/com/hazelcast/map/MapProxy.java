/*
 * Copyright (c) 2008-2012, Hazel Bilisim Ltd. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map;

import com.hazelcast.core.Transaction;
import com.hazelcast.spi.ServiceProxy;
import com.hazelcast.transaction.TransactionImpl;
import com.hazelcast.spi.Invocation;
import com.hazelcast.spi.NodeService;
import com.hazelcast.spi.impl.Response;
import com.hazelcast.nio.Data;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.map.MapService.MAP_SERVICE_NAME;
import static com.hazelcast.nio.IOUtil.toData;
import static com.hazelcast.nio.IOUtil.toObject;

public class MapProxy implements ServiceProxy {
    private final NodeService nodeService;
    private final MapService mapService;

    public MapProxy(final MapService mapService, NodeService nodeService) {
        this.mapService = mapService;
        this.nodeService = nodeService;
    }

    public Object remove(String name, Object k) {
        Data key = nodeService.toData(k);
        int partitionId = nodeService.getPartitionId(key);
        TransactionImpl txn = nodeService.getTransaction();
        String txnId = null;
        if (txn != null && txn.getStatus() == Transaction.TXN_STATUS_ACTIVE) {
            txnId = txn.getTxnId();
            txn.attachParticipant(MAP_SERVICE_NAME, partitionId);
        }
        RemoveOperation removeOperation = new RemoveOperation(name, toData(k), txnId);
        removeOperation.setValidateTarget(true);

        long backupCallId = mapService.createNewBackupCallQueue();
        try {
            removeOperation.setBackupCallId(backupCallId);
            removeOperation.setServiceName(MAP_SERVICE_NAME);
            Invocation invocation = nodeService.createInvocationBuilder(MAP_SERVICE_NAME, removeOperation, partitionId).build();
            Future f = invocation.invoke();
            Object response = f.get();
            Object returnObj = null;
            if (response instanceof Response) {
                Response r = (Response) response;
                returnObj = r.getResult();
            } else {
                returnObj = toObject(response);
            }
            if (returnObj instanceof Throwable) {
                throw (Throwable) returnObj;
            }
            UpdateResponse updateResponse = (UpdateResponse) returnObj;
            int backupCount = updateResponse.getBackupCount();
            if (backupCount > 0) {
                boolean backupsComplete = true;
                for (int i = 0; i < backupCount; i++) {
                    BlockingQueue backupResponses = mapService.getBackupCallQueue(backupCallId);
                    Object backupResponse = backupResponses.poll(3, TimeUnit.SECONDS);
                    if (backupResponse == null) {
                        backupsComplete = false;
                    }
                }
                if (!backupsComplete) {
                    for (int i = 0; i < backupCount; i++) {
                        Data dataValue = removeOperation.getValue();
                        GenericBackupOperation backupOp = new GenericBackupOperation(name, key, dataValue, -1, updateResponse.getVersion());
                        backupOp.setBackupOpType(GenericBackupOperation.BackupOpType.REMOVE);
                        backupOp.setInvocation(true);
                        Invocation backupInv = nodeService.createInvocationBuilder(MAP_SERVICE_NAME, backupOp,
                                partitionId).setReplicaIndex(i).build();
                        f = backupInv.invoke();
                        f.get(5, TimeUnit.SECONDS);
                    }
                }
            }
            return toObject(updateResponse.getOldValue());
        } catch (Throwable throwable) {
            throw (RuntimeException) throwable;
        } finally {
            mapService.removeBackupCallQueue(backupCallId);
        }
    }

    public Object put(String name, Object k, Object v, long ttl) {
        Data key = nodeService.toData(k);
        int partitionId = nodeService.getPartitionId(key);
        TransactionImpl txn = nodeService.getTransaction();
        String txnId = null;
        if (txn != null && txn.getStatus() == Transaction.TXN_STATUS_ACTIVE) {
            txnId = txn.getTxnId();
            txn.attachParticipant(MAP_SERVICE_NAME, partitionId);
        }
        PutOperation putOperation = new PutOperation(name, toData(k), v, txnId, ttl);
        putOperation.setValidateTarget(true);
        long backupCallId = mapService.createNewBackupCallQueue();
        try {
            putOperation.setBackupCallId(backupCallId);
            putOperation.setServiceName(MAP_SERVICE_NAME);
            Invocation invocation = nodeService.createInvocationBuilder(MAP_SERVICE_NAME, putOperation, partitionId).build();
            Future f = invocation.invoke();
            Object response = f.get();
            Object returnObj = null;
            if (response instanceof Response) {
                Response r = (Response) response;
                returnObj = r.getResult();
            } else {
                returnObj = toObject(response);
            }
            if (returnObj instanceof Throwable) {
                throw (Throwable) returnObj;
            }
            UpdateResponse updateResponse = (UpdateResponse) returnObj;
            int backupCount = updateResponse.getBackupCount();
            if (backupCount > 0) {
                boolean backupsComplete = true;
                for (int i = 0; i < backupCount; i++) {
                    BlockingQueue backupResponses = mapService.getBackupCallQueue(backupCallId);
                    Object backupResponse = backupResponses.poll(3, TimeUnit.SECONDS);
                    if (backupResponse == null) {
                        backupsComplete = false;
                    }
                }
                if (!backupsComplete) {
                    for (int i = 0; i < backupCount; i++) {
                        Data dataValue = putOperation.getValue();
                        GenericBackupOperation backupOp = new GenericBackupOperation(name, key, dataValue, ttl, updateResponse.getVersion());
                        backupOp.setInvocation(true);
                        Invocation backupInv = nodeService.createInvocationBuilder(MAP_SERVICE_NAME, backupOp,
                                partitionId).setReplicaIndex(i).build();
                        f = backupInv.invoke();
                        f.get(5, TimeUnit.SECONDS);
                    }
                }
            }
            return toObject(updateResponse.getOldValue());
        } catch (Throwable throwable) {
            throw (RuntimeException) throwable;
        } finally {
            mapService.removeBackupCallQueue(backupCallId);
        }
    }

    public Object getOperation(String name, Object k) {
        Data key = nodeService.toData(k);
        int partitionId = nodeService.getPartitionId(key);
        GetOperation getOperation = new GetOperation(name, toData(k));
        getOperation.setValidateTarget(true);
        getOperation.setServiceName(MAP_SERVICE_NAME);
        try {
            Invocation invocation = nodeService.createInvocationBuilder(MAP_SERVICE_NAME, getOperation, partitionId).build();
            Future f = invocation.invoke();
            Data response = (Data) f.get();
            return toObject(response);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public int getSize(String name) {
        try {
            MapSizeOperation mapSizeOperation = new MapSizeOperation(name);
            mapSizeOperation.setValidateTarget(true);
            Map<Integer, Object> results = nodeService.invokeOnAllPartitions(MAP_SERVICE_NAME, mapSizeOperation);
            int total = 0;
            for (Object result : results.values()) {
                Integer size = (Integer) nodeService.toObject(result);
                total += size;
            }
            return total;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}