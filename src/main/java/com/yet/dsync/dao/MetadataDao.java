/*
 * Copyright (c) 2017 Yuriy Tkach
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.yet.dsync.dao;

import com.yet.dsync.dto.DropboxFileData;
import com.yet.dsync.exception.DSyncClientException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

public class MetadataDao {

    public static final String CREATE_TABLE_STATEMENT = "CREATE TABLE METADATA ("
            + "ID       TEXT PRIMARY KEY  NOT NULL,"
            + "PATH     TEXT              NOT NULL,"
            + "PLOWER   TEXT              NOT NULL,"
            + "LOADED   INTEGER           NOT NULL,"
            + "REV      TEXT,"
            + "SIZE     INTEGER,"
            + "SRVDATE  INTEGER,"
            + "CLIDATE  INTEGER"
            + ")";

    private static final String SELECT_BY_ID_STATEMENT = "SELECT * FROM METADATA WHERE ID = ?";

    private static final String SELECT_NOT_LOADED_STATEMENT = "SELECT * FROM METADATA WHERE LOADED = 0";

    private static final String SELECT_BY_PLOWER_STATEMENT = "SELECT * FROM METADATA WHERE PLOWER = ?";

    private static final String INSERT_STATEMENT = "INSERT INTO METADATA ("
            + "ID,PATH,PLOWER,LOADED,REV,SIZE,SRVDATE,CLIDATE) VALUES (?,?,?,?,?,?,?,?)";

    private static final String UPDATE_LOADED_STATEMENT = "UPDATE METADATA SET LOADED = ? WHERE ID = ?";

    private static final String UPDATE_FIELDS_STATEMENT = "UPDATE METADATA SET PATH = ?,"
                                                                + "PLOWER = ?,"
                                                                + "REV = ?,"
                                                                + "SIZE = ?,"
                                                                + "SRVDATE = ?,"
                                                                + "CLIDATE = ?"
                                                                + " WHERE ID = ?";

    private static final String DELETE_BY_PATH_STATEMENT = "DELETE FROM METADATA WHERE PLOWER = ?";

    private static final int COL_ID = 1;
    private static final int COL_PATH = COL_ID + 1;
    private static final int COL_PATH_LOWER = COL_PATH + 1;
    private static final int COL_LOADED = COL_PATH_LOWER + 1;
    private static final int COL_REV = COL_LOADED + 1;
    private static final int COL_SIZE = COL_REV + 1;
    private static final int COL_SRVDATE = COL_SIZE + 1;
    private static final int COL_CLIDATE = COL_SRVDATE + 1;

    private final PreparedStatement readByIdStatement;
    private final PreparedStatement readNotLoadedStatement;
    private final PreparedStatement readByPLowerStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateLoadedStatement;
    private final PreparedStatement updateFieldsStatement;
    private final PreparedStatement deleteByPathStatement;

    public MetadataDao(final Connection connection) {
        try {
            readByIdStatement = connection.prepareStatement(SELECT_BY_ID_STATEMENT);
            readNotLoadedStatement = connection.prepareStatement(SELECT_NOT_LOADED_STATEMENT);
            readByPLowerStatement = connection.prepareStatement(SELECT_BY_PLOWER_STATEMENT);
            insertStatement = connection.prepareStatement(INSERT_STATEMENT);
            updateLoadedStatement = connection.prepareStatement(UPDATE_LOADED_STATEMENT);
            updateFieldsStatement = connection.prepareStatement(UPDATE_FIELDS_STATEMENT);
            deleteByPathStatement = connection.prepareStatement(DELETE_BY_PATH_STATEMENT);
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public DropboxFileData read(final String id) {
        try {
            readByIdStatement.setString(COL_ID, id);

            final ResultSet resultSet = readByIdStatement.executeQuery();
            if (resultSet.next()) {
                return buildFileData(resultSet);
            } else {
                return null;
            }
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public synchronized DropboxFileData readByLowerPath(final String lowerPath) {
        try {
            readByPLowerStatement.setString(COL_ID, lowerPath);

            final ResultSet resultSet = readByPLowerStatement.executeQuery();
            if (resultSet.next()) {
                return buildFileData(resultSet);
            } else {
                return null;
            }
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    private DropboxFileData buildFileData(final ResultSet resultSet) throws SQLException {
        final DropboxFileData.Builder builder = new DropboxFileData.Builder();
        final BigDecimal size = resultSet.getBigDecimal(COL_SIZE);
        builder
            .id(resultSet.getString(COL_ID))
            .pathDisplay(resultSet.getString(COL_PATH))
            .pathLower(resultSet.getString(COL_PATH_LOWER))
            .rev(resultSet.getString(COL_REV))
            .size(size != null ? size.longValue() : null)
            .serverModified(longToDateTime(resultSet.getBigDecimal(COL_SRVDATE)))
            .clientModified(longToDateTime(resultSet.getBigDecimal(COL_SRVDATE)));
        return builder.build();
    }

    public synchronized void write(final DropboxFileData fileData) {
        try {
            readByIdStatement.setString(COL_ID, fileData.getId());

            final ResultSet resultSet = readByIdStatement.executeQuery();
            if (resultSet.next()) {
                updateFieldsStatement.setString(1, fileData.getPathDisplay());
                updateFieldsStatement.setString(2, fileData.getPathLower());
                setStatementParams(updateFieldsStatement, 3, fileData.getRev(), Types.VARCHAR);
                setStatementParams(updateFieldsStatement, 4, fileData.getSize(), Types.BIGINT);
                setStatementParams(updateFieldsStatement, 5,
                        dateTimeToLong(fileData.getServerModified()), Types.BIGINT);
                setStatementParams(updateFieldsStatement, 6,
                        dateTimeToLong(fileData.getClientModified()), Types.BIGINT);

                updateFieldsStatement.setString(6, fileData.getId());

            } else {
                fillInsertStatement(fileData);

                insertStatement.executeUpdate();
            }
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    private void fillInsertStatement(final DropboxFileData fileData) throws SQLException {
        insertStatement.setString(COL_ID, fileData.getId());
        insertStatement.setString(COL_PATH, fileData.getPathDisplay());
        insertStatement.setString(COL_PATH_LOWER, fileData.getPathLower());
        insertStatement.setBoolean(COL_LOADED, false);
        setStatementParams(insertStatement, COL_REV, fileData.getRev(), Types.VARCHAR);
        setStatementParams(insertStatement, COL_SIZE, fileData.getSize(), Types.BIGINT);
        setStatementParams(insertStatement, COL_SRVDATE, dateTimeToLong(fileData.getServerModified()), Types.BIGINT);
        setStatementParams(insertStatement, COL_CLIDATE, dateTimeToLong(fileData.getClientModified()), Types.BIGINT);
    }

    public void write(final Set<DropboxFileData> fileDataSet) {
        try {
            fileDataSet.forEach(fileData -> {

                try {
                    fillInsertStatement(fileData);
                    insertStatement.addBatch();
                } catch (final SQLException ex) {
                    throw new DSyncClientException(ex);
                }

            });

            insertStatement.executeBatch();

        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public Collection<DropboxFileData> readAllNotLoaded() {
        try {
            final ResultSet resultSet = readNotLoadedStatement.executeQuery();

            final Collection<DropboxFileData> allFileData = new LinkedList<>();

            while (resultSet.next()) {
                final DropboxFileData fileData = buildFileData(resultSet);
                allFileData.add(fileData);
            }

            return allFileData;
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public synchronized void writeLoadedFlag(final String id, final boolean loaded) {
        try {
            updateLoadedStatement.setBoolean(1, loaded);
            updateLoadedStatement.setString(2, id);

            updateLoadedStatement.executeUpdate();
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    public void deleteByLowerPath(final String pathLower) {
        try {
            deleteByPathStatement.setString(1, pathLower);

            deleteByPathStatement.executeUpdate();
        } catch (final SQLException ex) {
            throw new DSyncClientException(ex);
        }
    }

    private void setStatementParams(final PreparedStatement statement,
                                    final int column, final Object data, final int sqlType) throws SQLException {
        if (data == null) {
            statement.setNull(column, sqlType);
        } else {
            statement.setObject(column, data, sqlType);
        }
    }

    private Long dateTimeToLong(final LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        } else {
            return dateTime.atZone(ZoneId.of("GMT")).toInstant().toEpochMilli();
        }
    }

    private LocalDateTime longToDateTime(final BigDecimal epochMilli) {
        if (epochMilli == null) {
            return null;
        } else {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli.longValue()), ZoneId.of("GMT"));
        }
    }

}
