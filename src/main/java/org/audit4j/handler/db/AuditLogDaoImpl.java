/*
 * Copyright (c) 2014-2015 Janith Bandara, This source is a part of
 * Audit4j - An open source auditing framework.
 * http://audit4j.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.audit4j.handler.db;

import static org.audit4j.handler.db.Utils.checkNotEmpty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.audit4j.core.dto.AuditEvent;
import org.audit4j.core.dto.Field;
import org.audit4j.core.exception.HandlerException;

/**
 * The Class HSQLAuditLogDao.
 *
 * @author <a href="mailto:janith3000@gmail.com">Janith Bandara</a>
 * @author Thebora Kompanioni https://github.com/theborakompanioni
 * @author Kettner, MA (ITOPT2) - KLM <Mark.Kettner@klm.com>
 */
final class AuditLogDaoImpl extends AuditBaseDao implements AuditLogDao {
    private final String tableName;
    private final String insertQuery;

    AuditLogDaoImpl(String tableName) throws HandlerException {
        this.tableName = checkNotEmpty(tableName, "Table name must not be empty");
        this.insertQuery = "insert into " + tableName
                + "(identifier, timestamp, actor, origin, action, elements) "
                + "values (?, ?, ?, ?, ?, ?)";

        createTableIfNotExists();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.audit4j.core.handler.db.AuditLogDao#createEvent(org.audit4j.core.
     * dto.AuditEvent)
     */

    /**
     * {@inheritDoc}
     *
     * @see org.audit4j.handler.db.AuditLogDao#writeEvent(org.audit4j.core.dto.AuditEvent)
     */
    @Override
    public boolean writeEvent(AuditEvent event) throws HandlerException {
        StringBuilder elements = new StringBuilder();
        for (Field element : event.getFields()) {
            elements.append(
                    element.getName() + " " + element.getType() + ":" + element.getValue() + ", ");
        }

        try (Connection conn = getConnection()) {
            try (PreparedStatement statement = conn.prepareStatement(insertQuery)) {
                statement.setString(1, event.getUuid().toString());
                statement.setTimestamp(2, new Timestamp(event.getTimestamp().getTime()));
                statement.setString(3, event.getActor());
                statement.setString(4, event.getOrigin());
                statement.setString(5, event.getAction());
                statement.setString(6, elements.toString());

                return statement.execute();
            }
        } catch (SQLException e) {
            throw new HandlerException("SQL Exception", DatabaseAuditHandler.class, e);
        }
    }

    private boolean createTableIfNotExists() throws HandlerException {
        boolean result = false;
        try (Connection conn = getConnection()) {
            StringBuilder query = new StringBuilder();
            if (isOracleDatabase()) {
                String values[] = tableName.split("\\.");
                query.append("select count(*) from all_tables where table_name = upper('")
                        .append(values[1]).append("') and tablespace_name = upper('")
                        .append(values[0]).append("')");
                try (PreparedStatement statement = conn.prepareStatement(query.toString())) {
                    result = statement.execute();
                }
                if (result == false) {
                    query.append("create table ").append(tableName).append(" (")
                            .append("identifier VARCHAR(200) NOT NULL,")
                            .append("timestamp TIMESTAMP NOT NULL,")
                            .append("actor VARCHAR2(200) NOT NULL,").append("origin VARCHAR2(200),")
                            .append("action VARCHAR2(200) NOT NULL,").append("elements CLOB")
                            .append(");");
                }
            } else if (isHSQLDatabase()) {
                query.append("create table if not exists ").append(tableName).append(" (")
                        .append("identifier VARCHAR(200) NOT NULL,")
                        .append("timestamp TIMESTAMP NOT NULL,")
                        .append("actor VARCHAR(200) NOT NULL,").append("origin VARCHAR(200),")
                        .append("action VARCHAR(200) NOT NULL,").append("elements LONGVARCHAR")
                        .append(");");
                try (PreparedStatement statement = conn.prepareStatement(query.toString())) {
                    result = statement.execute();
                }
            } else {
                query.append("create table if not exists ").append(tableName).append(" (")
                        .append("identifier VARCHAR(200) NOT NULL,")
                        .append("timestamp TIMESTAMP NOT NULL,")
                        .append("actor VARCHAR(200) NOT NULL,").append("origin VARCHAR(200),")
                        .append("action VARCHAR(200) NOT NULL,").append("elements TEXT")
                        .append(");");
                try (PreparedStatement statement = conn.prepareStatement(query.toString())) {
                    result = statement.execute();
                }
            }
            return result;
        } catch (SQLException e) {
            throw new HandlerException("SQL Exception", DatabaseAuditHandler.class, e);
        }
    }
}
