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

package org.apache.doris.analysis;

import org.apache.doris.catalog.Env;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.common.ErrorCode;
import org.apache.doris.common.ErrorReport;
import org.apache.doris.common.UserException;
import org.apache.doris.common.util.InternalDatabaseUtil;
import org.apache.doris.mysql.privilege.PrivPredicate;
import org.apache.doris.qe.ConnectContext;

// TRUNCATE TABLE tbl [PARTITION(p1, p2, ...)]
public class TruncateTableStmt extends DdlStmt implements NotFallbackInParser {

    private TableRef tblRef;
    private boolean forceDrop;

    public TruncateTableStmt(TableRef tblRef, boolean forceDrop) {
        this.tblRef = tblRef;
        this.forceDrop = forceDrop;
    }

    public TableRef getTblRef() {
        return tblRef;
    }

    public boolean isForceDrop() {
        return forceDrop;
    }

    @Override
    public void analyze(Analyzer analyzer) throws AnalysisException, UserException {
        super.analyze(analyzer);
        tblRef.getName().analyze(analyzer);

        if (tblRef.hasExplicitAlias()) {
            throw new AnalysisException("Not support truncate table with alias");
        }
        InternalDatabaseUtil.checkDatabase(tblRef.getName().getDb(), ConnectContext.get());
        // check access
        // it requires LOAD privilege, because we consider this operation as 'delete data', which is also a
        // 'load' operation.
        if (!Env.getCurrentEnv().getAccessManager()
                .checkTblPriv(ConnectContext.get(), tblRef.getName().getCtl(), tblRef.getName().getDb(),
                        tblRef.getName().getTbl(), PrivPredicate.LOAD)) {
            ErrorReport.reportAnalysisException(ErrorCode.ERR_SPECIFIC_ACCESS_DENIED_ERROR, "LOAD");
        }

        // check partition if specified. do not support truncate temp partitions
        PartitionNames partitionNames = tblRef.getPartitionNames();
        if (partitionNames != null) {
            partitionNames.analyze(analyzer);
            if (partitionNames.isTemp()) {
                throw new AnalysisException("Not support truncate temp partitions");
            }
        }
    }

    @Override
    public String toSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("TRUNCATE TABLE ");
        sb.append(tblRef.getName().toSql());
        if (tblRef.getPartitionNames() != null) {
            sb.append(tblRef.getPartitionNames().toSql());
        }
        if (isForceDrop()) {
            sb.append(" FORCE");
        }
        return sb.toString();
    }

    public String toSqlWithoutTable() {
        StringBuilder sb = new StringBuilder();
        if (tblRef.getPartitionNames() != null) {
            sb.append(tblRef.getPartitionNames().toSql());
        }
        if (isForceDrop()) {
            sb.append(" FORCE");
        }
        return sb.toString();
    }

    @Override
    public StmtType stmtType() {
        return StmtType.TRUNCATE;
    }
}
