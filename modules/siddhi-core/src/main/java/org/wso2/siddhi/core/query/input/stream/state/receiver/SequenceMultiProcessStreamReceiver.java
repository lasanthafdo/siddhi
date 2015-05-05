/*
 * Copyright (c) 2005 - 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.wso2.siddhi.core.query.input.stream.state.receiver;

import org.wso2.siddhi.core.query.input.MultiProcessStreamReceiver;
import org.wso2.siddhi.core.query.input.stream.state.StateStreamRuntime;

public class SequenceMultiProcessStreamReceiver extends MultiProcessStreamReceiver {


    private StateStreamRuntime stateStreamRuntime;

    public SequenceMultiProcessStreamReceiver(String streamId, int processCount, StateStreamRuntime stateStreamRuntime) {
        super(streamId, processCount);
        this.stateStreamRuntime = stateStreamRuntime;
        eventSequence = new int[processCount];
        int count=0;
        for (int i = eventSequence.length - 1; i >= 0; i--) {
            eventSequence[count] = i;
            count++;
        }
    }

    public SequenceMultiProcessStreamReceiver clone(String key) {
        return new SequenceMultiProcessStreamReceiver(streamId + key, processCount, null);
    }

    public void setStateStreamRuntime(StateStreamRuntime stateStreamRuntime) {
        this.stateStreamRuntime = stateStreamRuntime;
    }

    protected void stabilizeStates() {
        stateStreamRuntime.resetAndUpdate();
    }
}
