/*
 * Copyright (c) 2008-2012, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.impl.partition;

import com.hazelcast.impl.MemberImpl;

import java.util.Collection;
import java.util.List;

public interface PartitionStateGenerator {

    PartitionInfo[] initialize(final Collection<MemberImpl> members, final int partitionCount);

    PartitionInfo[] reArrange(final PartitionInfo[] currentState,
                              final Collection<MemberImpl> members,
                              final int partitionCount,
                              final List<MigrationRequestTask> lostPartitionTasksList,
                              final List<MigrationRequestTask> immediateTasksList,
                              final List<MigrationRequestTask> scheduledTasksList);
}
