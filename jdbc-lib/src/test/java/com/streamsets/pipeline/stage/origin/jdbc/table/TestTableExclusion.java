/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.jdbc.table;

import com.google.common.collect.ImmutableSet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

public class TestTableExclusion {
  private static final String USER_NAME = "sa";
  private static final String PASSWORD = "sa";
  protected static final String database = "TEST";
  private static final String JDBC_URL = "jdbc:h2:mem:" + database;
  private static final String CREATE_TABLE_PATTERN = "CREATE TABLE IF NOT EXISTS TEST.%s (p_id INT NOT NULL PRIMARY KEY);";
  private static final String DELETE_TABLE_PATTERN = "DROP TABLE IF EXISTS TEST.%s;";

  private static final Set<String> TABLE_NAMES =
      ImmutableSet.of(
          "TABLEA", "TABLEB", "TABLEC", "TABLED", "TABLEE",
          "TABLE1", "TABLE2", "TABLE3", "TABLE4", "TABLE5"
      );

  private static Connection connection;

  @BeforeClass
  public static void setup() throws SQLException {
    connection = DriverManager.getConnection(JDBC_URL, USER_NAME, PASSWORD);
    try (Statement s = connection.createStatement()) {
      s.addBatch("CREATE SCHEMA IF NOT EXISTS TEST;");
      for (String tableName : TABLE_NAMES) {
        s.addBatch(String.format(CREATE_TABLE_PATTERN, tableName));
      }
      s.executeBatch();
    }
  }

  @AfterClass
  public static void tearDown() throws SQLException {
    try (Statement s = connection.createStatement()) {
      for (String tableName : TABLE_NAMES) {
        s.addBatch(String.format(DELETE_TABLE_PATTERN, tableName));
      }
      s.addBatch("DROP SCHEMA IF EXISTS TEST;");
      s.executeBatch();
    }
    connection.close();
  }

  @Test
  public void testNoExclusionPattern() throws Exception {
    TableConfigBean tableConfigBean = new TableConfigBean();
    tableConfigBean.schema = database;
    tableConfigBean.tablePattern = "%";
    Assert.assertEquals(TABLE_NAMES.size(), TableContextUtil.listTablesForConfig(connection, tableConfigBean).size());
  }

  @Test
  public void testExcludeEverything() throws Exception {
    TableConfigBean tableConfigBean = new TableConfigBean();
    tableConfigBean.schema = database;
    tableConfigBean.tablePattern = "%";
    tableConfigBean.tableExclusionPattern = ".*";
    Assert.assertEquals(0, TableContextUtil.listTablesForConfig(connection, tableConfigBean).size());
  }

  @Test
  public void testExcludeEndingWithNumbers() throws Exception {
    TableConfigBean tableConfigBean = new TableConfigBean();
    tableConfigBean.schema = database;
    tableConfigBean.tablePattern = "TABLE%";
    //Exclude tables ending with [0-9]+
    tableConfigBean.tableExclusionPattern = "TABLE[0-9]+";
    Assert.assertEquals(5, TableContextUtil.listTablesForConfig(connection, tableConfigBean).size());
  }

  @Test
  public void testExcludeTableNameAsRegex() throws Exception {
    TableConfigBean tableConfigBean = new TableConfigBean();
    tableConfigBean.schema = database;
    tableConfigBean.tablePattern = "TABLE%";
    tableConfigBean.tableExclusionPattern = "TABLE1";
    Assert.assertEquals(9, TableContextUtil.listTablesForConfig(connection, tableConfigBean).size());
  }

  @Test
  public void testExcludeUsingOrRegex() throws Exception {
    TableConfigBean tableConfigBean = new TableConfigBean();
    tableConfigBean.schema = database;
    tableConfigBean.tablePattern = "TABLE%";
    tableConfigBean.tableExclusionPattern = "TABLE1|TABLE2";
    Assert.assertEquals(8, TableContextUtil.listTablesForConfig(connection, tableConfigBean).size());
  }

}
