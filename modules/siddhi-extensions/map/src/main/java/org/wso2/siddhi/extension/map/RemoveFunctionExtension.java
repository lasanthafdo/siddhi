/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.siddhi.extension.map;

import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.exception.ExecutionPlanRuntimeException;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.function.FunctionExecutor;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.exception.ExecutionPlanValidationException;

import java.util.HashMap;
import java.util.Map;

/**
 * remove(HashMap , key)
 * Returns the updated hashmap
 * Accept Type(s): (HashMap , ValidAttributeType)
 * Return Type(s): HashMap
 */
public class RemoveFunctionExtension extends FunctionExecutor {
    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(RemoveFunctionExtension.class);
    Attribute.Type returnType = Attribute.Type.OBJECT;
    private Map hashMap;

    @Override
    protected void init(ExpressionExecutor[] attributeExpressionExecutors, ExecutionPlanContext executionPlanContext) {
        if (attributeExpressionExecutors.length < 1) {
            throw new ExecutionPlanValidationException("Invalid no of arguments passed to map:remove() function, " +
                    "required one or more, but found " + attributeExpressionExecutors.length);
        }
    }

    @Override
    protected Object execute(Object[] data) {
        if (data == null) {
            throw new ExecutionPlanRuntimeException("Data can not be null.");
        }
        if (data.length < 2) {
            throw new ExecutionPlanRuntimeException("Too few values for data");
        }
        hashMap = (HashMap) data[0];
        for (int i = 1; i < data.length; i++) {
            if (hashMap.get(data[i]) == null) {
                log.warn("Key value '" + data[i] + "' is not existed.");
            } else {
                hashMap.remove(data[i]);
            }
        }
        return hashMap;
    }

    @Override
    protected Object execute(Object data) {
        return null;
    }

    @Override
    public void start() {
        //Nothing to start
    }

    @Override
    public void stop() {
        //Nothing to stop
    }

    @Override
    public Attribute.Type getReturnType() {
        return returnType;
    }

    @Override
    public Object[] currentState() {
        return null;    //No need to maintain a state.
    }

    @Override
    public void restoreState(Object[] state) {
        //Since there's no need to maintain a state, nothing needs to be done here.
    }
}

