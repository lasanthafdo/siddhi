/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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


package org.wso2.siddhi.extension.eventtable;

import org.apache.log4j.Logger;
import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.MetaComplexEvent;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.exception.CannotLoadConfigurationException;
import org.wso2.siddhi.core.exception.ExecutionPlanCreationException;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.table.EventTable;
import org.wso2.siddhi.core.util.SiddhiConstants;
import org.wso2.siddhi.core.util.collection.operator.Finder;
import org.wso2.siddhi.core.util.collection.operator.Operator;
import org.wso2.siddhi.extension.eventtable.cache.CachingTable;
import org.wso2.siddhi.extension.eventtable.rdbms.*;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.TableDefinition;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.util.AnnotationHelper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class RDBMSEventTable implements EventTable {

    private TableDefinition tableDefinition;
    private DBHandler dbHandler;
    private CachingTable cachedTable;
    private boolean isCachingEnabled;
    private static final Logger log = Logger.getLogger(RDBMSEventTable.class);

    /*
    Loads rdbms-table-config.xml file which provides DB mapping details
     */
    static {
        try {
            DBQueryHelper.loadConfiguration();
        } catch (CannotLoadConfigurationException e) {
            throw new ExecutionPlanCreationException("Error while loading the rdbms configuration file", e);
        }
    }

    /**
     * Event Table initialization method, it checks the annotation and do necessary pre configuration tasks.
     * @param tableDefinition Definition of event table
     * @param executionPlanContext ExecutionPlan related meta information
     */
    public void init(TableDefinition tableDefinition, ExecutionPlanContext executionPlanContext) {
        this.tableDefinition = tableDefinition;
        Connection con = null;
        int bloomFilterSize = RDBMSEventTableConstants.BLOOM_FILTER_SIZE;
        int bloomFilterHashFunctions = RDBMSEventTableConstants.BLOOM_FILTER_HASH_FUNCTIONS;

        Annotation fromAnnotation = AnnotationHelper.getAnnotation(SiddhiConstants.ANNOTATION_FROM,
                tableDefinition.getAnnotations());
        String dataSourceName = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_DATASOURCE_NAME);
        String tableName = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_TABLE_NAME);
        DataSource dataSource = executionPlanContext.getSiddhiContext().getSiddhiDataSource(dataSourceName);
        List<Attribute> attributeList = tableDefinition.getAttributeList();

        if (dataSourceName == null || tableName == null) {
            throw new ExecutionPlanCreationException("Invalid query specified. Required properties (datasourceName or/and tableName) not found ");
        }

        String cacheType = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_CACHE);
        String cacheSizeInString = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_CACHE_SIZE);
        String bloomsEnabled = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_BLOOM_FILTERS);

        try {
            this.dbHandler = new DBHandler(dataSource, tableName, attributeList, tableDefinition);

            if ((con = dataSource.getConnection()) == null) {
                throw new ExecutionPlanCreationException("Error while making connection to database");
            }

            if (cacheType != null) {
                cachedTable = new CachingTable(cacheType, cacheSizeInString, executionPlanContext, tableDefinition);
                isCachingEnabled = true;
            } else if (bloomsEnabled != null && bloomsEnabled.equalsIgnoreCase("enable")) {
                String bloomsFilterSize = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_BLOOM_FILTERS_SIZE);
                String bloomsFilterHash = fromAnnotation.getElement(RDBMSEventTableConstants.ANNOTATION_ELEMENT_BLOOM_FILTERS_HASH);
                if (bloomsFilterSize != null) {
                    bloomFilterSize = Integer.parseInt(bloomsFilterSize);
                }
                if (bloomsFilterHash != null) {
                    bloomFilterHashFunctions = Integer.parseInt(bloomsFilterHash);
                }

                dbHandler.setBloomFilterProperties(bloomFilterSize, bloomFilterHashFunctions);
                dbHandler.buildBloomFilters();
            }

        } catch (SQLException e) {
            throw new ExecutionPlanCreationException("Error while making connection to database", e);
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException e) {
                    log.error("unable to release connection", e);
                }
            }
        }
    }

    @Override
    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }

    /**
     * Called when adding an event to the event table
     * @param addingEventChunk input event list
     */
    @Override
    public void add(ComplexEventChunk<StreamEvent> addingEventChunk) {
        dbHandler.addEvent(addingEventChunk, cachedTable);
    }

    /**
     * Called when deleting an event chunk from event table
     * @param deletingEventChunk Event list for deletion
     * @param operator Operator that perform RDBMS related operations
     */
    @Override
    public void delete(ComplexEventChunk<StreamEvent> deletingEventChunk, Operator operator) {
        operator.delete(deletingEventChunk, null);
        if (isCachingEnabled) {
            ((RDBMSOperator) operator).getInMemoryEventTableOperator().delete(deletingEventChunk, cachedTable.getCacheList());
        }
    }

    /**
     * Called when updating the event table entries
     * @param updatingEventChunk Event list that needs to be updated
     * @param operator Operator that perform RDBMS related operations
     */
    @Override
    public void update(ComplexEventChunk<StreamEvent> updatingEventChunk, Operator operator, int[] mappingPosition) {
        operator.update(updatingEventChunk, null, null);
        if (isCachingEnabled) {
            ((RDBMSOperator) operator).getInMemoryEventTableOperator().update(updatingEventChunk, cachedTable.getCacheList(), mappingPosition);
        }
    }

    /**
     * Called when having "in" condition, to check the existence of the event
     * @param matchingEvent Event that need to be check for existence
     * @param finder Operator that perform RDBMS related search
     */
    @Override
    public boolean contains(ComplexEvent matchingEvent, Finder finder) {
        if (isCachingEnabled) {
            return ((RDBMSOperator) finder).getInMemoryEventTableOperator().contains(matchingEvent, cachedTable.getCacheList()) || finder.contains(matchingEvent, null);
        }
        return finder.contains(matchingEvent, null);
    }

    /**
     * Called to construct a operator to perform delete and update operations
     */
    @Override
    public Operator constructOperator(Expression expression, MetaComplexEvent metaComplexEvent, ExecutionPlanContext executionPlanContext, List<VariableExpressionExecutor> variableExpressionExecutors, Map<String, EventTable> eventTableMap, int matchingStreamIndex, long withinTime) {
        return RDBMSOperatorParser.parse(dbHandler, expression, metaComplexEvent, executionPlanContext, variableExpressionExecutors, eventTableMap, matchingStreamIndex, tableDefinition, withinTime, cachedTable);
    }


    /**
     * Called to find a event from event table
     */
    @Override
    public StreamEvent find(ComplexEvent matchingEvent, Finder finder) {
        return finder.find(matchingEvent, null, null);
    }

    /**
     * Called to construct a operator to perform search operations
     */
    @Override
    public Finder constructFinder(Expression expression, MetaComplexEvent metaComplexEvent, ExecutionPlanContext executionPlanContext, List<VariableExpressionExecutor> variableExpressionExecutors, Map<String, EventTable> eventTableMap, int matchingStreamIndex, long withinTime) {
        return RDBMSOperatorParser.parse(dbHandler, expression, metaComplexEvent, executionPlanContext, variableExpressionExecutors, eventTableMap, matchingStreamIndex, tableDefinition, withinTime, cachedTable);
    }


}
