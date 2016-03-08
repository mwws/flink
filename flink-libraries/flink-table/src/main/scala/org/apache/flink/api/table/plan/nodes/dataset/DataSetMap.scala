/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.api.table.plan.nodes.dataset

import org.apache.calcite.plan.{RelOptCluster, RelTraitSet}
import org.apache.calcite.rel.`type`.RelDataType
import org.apache.calcite.rel.{RelNode, RelWriter, SingleRel}
import org.apache.flink.api.common.functions.{MapFunction, MapPartitionFunction}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.DataSet
import org.apache.flink.api.table.plan.{PlanGenException, TypeConverter}
import org.apache.flink.api.table.plan.TypeConverter._
import org.apache.flink.api.table.typeinfo.RowTypeInfo
import org.apache.flink.api.table.{Row, TableConfig}

import scala.collection.JavaConverters._

/**
  * Flink RelNode which matches along with MapOperator.
  *
  */
class DataSetMap(
    cluster: RelOptCluster,
    traitSet: RelTraitSet,
    input: RelNode,
    rowType: RelDataType,
    opName: String,
    func: (TableConfig, TypeInformation[Any], TypeInformation[Any]) => MapFunction[Any, Any])
  extends SingleRel(cluster, traitSet, input)
  with DataSetRel {

  override def deriveRowType() = rowType

  override def copy(traitSet: RelTraitSet, inputs: java.util.List[RelNode]): RelNode = {
    new DataSetMap(
      cluster,
      traitSet,
      inputs.get(0),
      rowType,
      opName,
      func
    )
  }

  override def explainTerms(pw: RelWriter): RelWriter = {
    super.explainTerms(pw).item("name", opName)
  }

  override def toString = opName

  override def translateToPlan(config: TableConfig,
      expectedType: Option[TypeInformation[Any]]): DataSet[Any] = {

    expectedType match {
      case Some(typeInfo) if typeInfo.getTypeClass != classOf[Row] =>
        throw new PlanGenException("GroupReduce operations " +
            "currently only support returning Rows.")
      case _ => // ok
    }

    val inputDS = input.asInstanceOf[DataSetRel].translateToPlan(
      config,
      // tell the input operator that this operator currently only supports Rows as input
      Some(TypeConverter.DEFAULT_ROW_TYPE))

    val returnType = determineReturnType(
      getRowType,
      expectedType,
      config.getNullCheck,
      config.getEfficientTypeUsage)
    val mapFunc = func.apply(config, inputDS.getType, returnType)
    inputDS.map(mapFunc)
  }
}
