// Copyright 2021-present StarRocks, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// This file is based on code available under the Apache license here:
//   https://github.com/apache/incubator-doris/blob/master/fe/fe-core/src/main/java/org/apache/doris/http/rest/BootstrapFinishAction.java

// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.starrocks.http.rest;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.starrocks.common.Config;
import com.starrocks.common.DdlException;
import com.starrocks.common.Version;
import com.starrocks.http.ActionController;
import com.starrocks.http.BaseRequest;
import com.starrocks.http.BaseResponse;
import com.starrocks.http.IllegalArgException;
import com.starrocks.server.GlobalStateMgr;
import io.netty.handler.codec.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 * fe_host:fe_http_port/api/bootstrap
 * return:
 * {"status":"OK","msg":"Success","replayedJournal"=123456, "queryPort"=9000, "rpcPort"=9001}
 * {"status":"FAILED","msg":"err info..."}
 */
public class BootstrapFinishAction extends RestBaseAction {
    private static final Logger LOG = LogManager.getLogger(BootstrapFinishAction.class);

    private static final String CLUSTER_ID = "cluster_id";
    private static final String TOKEN = "token";

    public static final String REPLAYED_JOURNAL_ID = "replayedJournalId";
    public static final String QUERY_PORT = "queryPort";
    public static final String RPC_PORT = "rpcPort";
    public static final String FE_START_TIME = "feStartTime";
    public static final String FE_VERSION = "feVersion";

    public BootstrapFinishAction(ActionController controller) {
        super(controller);
    }

    public static void registerAction(ActionController controller) throws IllegalArgException {
        controller.registerHandler(HttpMethod.GET, "/api/bootstrap", new BootstrapFinishAction(controller));
    }

    @Override
    public void execute(BaseRequest request, BaseResponse response) throws DdlException {
        boolean isReady = GlobalStateMgr.getCurrentState().isReady();

        // to json response
        BootstrapResult result;
        if (isReady) {
            result = new BootstrapResult();
            String clusterIdStr = request.getSingleParameter(CLUSTER_ID);
            String token = request.getSingleParameter(TOKEN);
            if (!Strings.isNullOrEmpty(clusterIdStr) && !Strings.isNullOrEmpty(token)) {
                // cluster id or token is provided, return more info
                int clusterId = 0;
                try {
                    clusterId = Integer.parseInt(clusterIdStr);
                } catch (NumberFormatException e) {
                    result.status = ActionStatus.FAILED;
                    LOG.info("invalid cluster id format: {}", clusterIdStr);
                    result.msg = "invalid parameter";
                }

                if (result.status == ActionStatus.OK) {
                    if (clusterId != GlobalStateMgr.getCurrentState().getNodeMgr().getClusterId()) {
                        result.status = ActionStatus.FAILED;
                        LOG.info("invalid cluster id: {}", clusterIdStr);
                        result.msg = "invalid parameter";
                    }
                }

                if (result.status == ActionStatus.OK) {
                    if (!token.equals(GlobalStateMgr.getCurrentState().getNodeMgr().getToken())) {
                        result.status = ActionStatus.FAILED;
                        LOG.info("invalid token: {}", token);
                        result.msg = "invalid parameter";
                    }
                }

                if (result.status == ActionStatus.OK) {
                    // cluster id and token are valid, return replayed journal id
                    long replayedJournalId = GlobalStateMgr.getCurrentState().getReplayedJournalId();
                    long feStartTime = GlobalStateMgr.getCurrentState().getFeStartTime();
                    result.setMaxReplayedJournal(replayedJournalId);
                    result.setQueryPort(Config.query_port);
                    result.setRpcPort(Config.rpc_port);
                    result.setFeStartTime(feStartTime);
                    result.setFeVersion(Version.STARROCKS_VERSION + "-" + Version.STARROCKS_COMMIT_HASH);
                }
            }
        } else {
            result = new BootstrapResult("not ready");
        }

        // send result
        response.setContentType("application/json");
        response.getContent().append(result.toJson());
        sendResult(request, response);
    }

    public static class BootstrapResult extends RestBaseResult {
        private long replayedJournalId = 0;
        private int queryPort = 0;
        private int rpcPort = 0;
        private long feStartTime = 0;
        private String feVersion;

        public BootstrapResult() {
            super();
        }

        public BootstrapResult(String msg) {
            super(msg);
        }

        public void setMaxReplayedJournal(long replayedJournalId) {
            this.replayedJournalId = replayedJournalId;
        }

        public long getMaxReplayedJournal() {
            return replayedJournalId;
        }

        public void setQueryPort(int queryPort) {
            this.queryPort = queryPort;
        }

        public int getQueryPort() {
            return queryPort;
        }

        public void setRpcPort(int rpcPort) {
            this.rpcPort = rpcPort;
        }

        public int getRpcPort() {
            return rpcPort;
        }

        public long getFeStartTime() {
            return feStartTime;
        }

        public void setFeStartTime(long feStartTime) {
            this.feStartTime = feStartTime;
        }

        public String getFeVersion() {
            return feVersion;
        }

        public void setFeVersion(String feVersion) {
            this.feVersion = feVersion;
        }

        @Override
        public String toJson() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }
}
