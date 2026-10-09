"""
Sawc MDB Database Reader for Sejong Tablet Press (MC081).
Reads SawcData.mdb (Alarms, Operations, Logins, Telemetry) and Sawc.mdb (Code definitions).
"""

import os
import json
import logging
from pathlib import Path
from datetime import datetime
from typing import Dict, Any, List, Optional, Tuple

try:
    import win32com.client
except ImportError:
    win32com = None

from .cleaner import clean_str, parse_numeric, parse_datetime

logger = logging.getLogger("compression.mdb_reader")


class SawcMdbReader:
    """Reads SawcData.mdb and Sawc.mdb using ADODB COM connection, or staging JSON on Linux."""

    def __init__(self, base_dir: str = "."):
        self.base_dir = os.path.abspath(base_dir)
        self.sawc_mdb_path = os.path.join(self.base_dir, "Sawc.mdb")
        self.sawc_data_mdb_path = os.path.join(self.base_dir, "SawcData.mdb")
        self._alarm_dict: Dict[str, str] = {}
        self._operation_dict: Dict[str, str] = {}
        self._users_dict: Dict[str, Dict[str, Any]] = {}
        self._init_dictionaries()

    def _get_connection(self, mdb_path: str):
        if win32com is None:
            return None
        if not os.path.exists(mdb_path):
            return None
        try:
            conn = win32com.client.Dispatch("ADODB.Connection")
            conn_str = f"Provider=Microsoft.ACE.OLEDB.16.0;Data Source={mdb_path};"
            conn.Open(conn_str)
            return conn
        except Exception as e:
            logger.warning(f"Could not open MDB via COM: {e}")
            return None

    def _init_dictionaries(self):
        """Loads alarm and operation description mappings from Sawc.mdb."""
        if win32com is None or not os.path.exists(self.sawc_mdb_path):
            self._init_fallback_dicts()
            return

        try:
            conn = self._get_connection(self.sawc_mdb_path)
            
            # Load English table
            try:
                rs, _ = conn.Execute("SELECT GroupA, GroupB, GroupC, original, Description FROM English")
                while not rs.EOF:
                    ga = str(rs.Fields(0).Value or "").strip()
                    gb = str(rs.Fields(1).Value or "").strip()
                    gc = str(rs.Fields(2).Value or "").strip()
                    orig = str(rs.Fields(3).Value or "").strip()
                    desc = str(rs.Fields(4).Value or "").strip()
                    label = orig if orig else desc

                    if ga == "030":
                        # GroupC code e.g. '001' -> '0010'
                        try:
                            code_int = int(gc)
                            key_b = f"{code_int * 10:04d}"
                            self._alarm_dict[key_b] = label
                        except Exception:
                            pass
                    elif ga == "010":
                        try:
                            code_int = int(gc)
                            key_b = f"{code_int * 10:04d}"
                            self._operation_dict[key_b] = label
                        except Exception:
                            pass
                    rs.MoveNext()
                rs.Close()
            except Exception as e:
                logger.warning(f"Could not load English table from Sawc.mdb: {e}")

            # Load login table
            try:
                rs, _ = conn.Execute("SELECT UserID, UserName, Authority FROM login")
                while not rs.EOF:
                    uid = str(rs.Fields(0).Value or "").strip()
                    uname = str(rs.Fields(1).Value or "").strip()
                    auth = str(rs.Fields(2).Value or "").strip()
                    if uid:
                        self._users_dict[uid] = {"userName": uname, "authority": auth}
                    rs.MoveNext()
                rs.Close()
            except Exception as e:
                logger.warning(f"Could not load login table: {e}")

            conn.Close()
        except Exception as e:
            logger.warning(f"Error opening Sawc.mdb: {e}")
            self._init_fallback_dicts()

    def _init_fallback_dicts(self):
        """Fallback dictionary for common codes."""
        self._alarm_dict = {
            "0010": "E.M.G S/W Pushing",
            "0020": "Scraper Alarm",
            "0030": "Upper Punch Tightness",
            "0040": "Upper Punch Safety Rail",
            "0050": "Main Motor Overload",
            "0060": "Target Quantity Stop",
            "0070": "Oil Lubrication Alarm",
            "0080": "Insert Handle",
            "0090": "Dust Collector Trip",
            "0100": "Safety Message",
            "0110": "Door Opened",
            "0120": "Powder Shortage",
            "0130": "Lower Punch Tightness",
            "0140": "Lower Punch Safety Rail",
            "0150": "Feeder Overload",
            "0160": "Low Air Pressure",
            "0170": "Linear Alarm",
            "0180": "A.W.C Stop",
            "0190": "External Interruption",
            "0200": "Hydraulic Alarm",
            "0210": "Ejection Force Alarm",
            "0220": "Tablet Alarm",
            "1010": "AWC Upper Limit Warning",
            "1020": "AWC Lower Limit Warning",
            "1070": "Main Pressure High Alarm",
            "1080": "Main Pressure Low Alarm",
            "1220": "Punch Tightness Alarm",
            "1230": "Punch Tightness Warning",
            "1250": "Ejection Force High Alarm",
            "1260": "Ejection Force Low Alarm",
            "1470": "Tablet Hardness Alarm",
            "1520": "A.W.C Control Stop",
            "1530": "Safety Guard Door Interlock"
        }
        self._operation_dict = {
            "0010": "Disk Speed Change",
            "0020": "Disk Speed Setup",
            "0030": "Feeder Speed Change",
            "0040": "Feeder Speed Setup",
            "0050": "Pre-Pressure Change",
            "0060": "Pre-Section Setup",
            "0070": "Main-Pressure Change",
            "0080": "Main-Section Setup",
            "0980": "Operational Parameter Change",
            "0990": "System Mode Transition"
        }

    def get_user_info(self, user_id: str) -> Dict[str, Any]:
        """Returns user name and authority for given user_id."""
        if not user_id:
            return {"userName": "", "authority": ""}
        return self._users_dict.get(user_id, {"userName": user_id, "authority": ""})

    def get_alarm_name(self, class_b: str) -> str:
        """Resolves alarm message description from ClassB code."""
        code = str(class_b or "").strip().zfill(4)
        return self._alarm_dict.get(code, f"Alarm Code {code}")

    def get_operation_name(self, class_b: str) -> str:
        """Resolves operation description from ClassB code."""
        code = str(class_b or "").strip().zfill(4)
        return self._operation_dict.get(code, f"Parameter Setup ({code})")

    def read_sawc_events(self, target_date_str: str) -> Dict[str, List[Dict[str, Any]]]:
        """
        Reads SAWC_DATA_<YYYYMMDD> table from SawcData.mdb.
        Returns alarms, operations (audits), and login/logout events.
        target_date_str can be 'YYYY-MM-DD' or 'YYYYMMDD'.
        """
        date_clean = target_date_str.replace("-", "")
        table_name = f"SAWC_DATA_{date_clean}"

        alarms: List[Dict[str, Any]] = []
        operations: List[Dict[str, Any]] = []
        logins: List[Dict[str, Any]] = []

        if not os.path.exists(self.sawc_data_mdb_path):
            logger.warning(f"SawcData.mdb not found at {self.sawc_data_mdb_path}")
            return {"alarms": alarms, "operations": operations, "logins": logins}

        # Check staged extracted JSON fallback first if COM is unavailable or MDB not present
        staged_candidates = [
            os.path.join("staging", "extracted_json", target_date_str),
            os.path.join(self.base_dir, "..", "file_ingestion_service", "staging", "extracted_json", target_date_str),
            os.path.join(self.base_dir, "staging", "extracted_json", target_date_str),
            os.path.join(Path(__file__).resolve().parent.parent / "staging" / "extracted_json" / target_date_str),
        ]
        for sdir in staged_candidates:
            if os.path.isdir(str(sdir)):
                sawc_json = os.path.join(sdir, f"SawcEvents-{target_date_str}.json")
                if os.path.isfile(sawc_json):
                    try:
                        with open(sawc_json, "r", encoding="utf-8") as f:
                            data = json.load(f)
                            return {
                                "alarms": data.get("alarms", []),
                                "operations": data.get("operations", []),
                                "logins": data.get("logins", []),
                            }
                    except Exception as e:
                        logger.warning(f"Failed to read {sawc_json}: {e}")

                alm_json = os.path.join(sdir, f"AlarmHistory-{target_date_str}.json")
                ops_json = os.path.join(sdir, f"OperatingHistory-{target_date_str}.json")
                log_json = os.path.join(sdir, f"LogInOutHistory-{target_date_str}.json")
                if os.path.isfile(alm_json) or os.path.isfile(ops_json):
                    loaded_alms = []
                    loaded_ops = []
                    loaded_logs = []
                    if os.path.isfile(alm_json):
                        with open(alm_json, "r", encoding="utf-8") as f:
                            loaded_alms = json.load(f).get("alarms", [])
                    if os.path.isfile(ops_json):
                        with open(ops_json, "r", encoding="utf-8") as f:
                            loaded_ops = json.load(f).get("operations", [])
                    if os.path.isfile(log_json):
                        with open(log_json, "r", encoding="utf-8") as f:
                            ld = json.load(f)
                            loaded_logs = ld.get("loginHistory", []) or ld.get("logins", [])
                    return {"alarms": loaded_alms, "operations": loaded_ops, "logins": loaded_logs}

        if win32com is None or not os.path.exists(self.sawc_data_mdb_path):
            logger.info(f"COM unavailable or SawcData.mdb not found; checked staging directories for {target_date_str}.")
            return {"alarms": alarms, "operations": operations, "logins": logins}

        try:
            conn = self._get_connection(self.sawc_data_mdb_path)
            if conn is None:
                return {"alarms": alarms, "operations": operations, "logins": logins}
            
            # Check if table exists
            rs_schema = conn.OpenSchema(20)
            table_exists = False
            while not rs_schema.EOF:
                if rs_schema.Fields("TABLE_NAME").Value == table_name:
                    table_exists = True
                    break
                rs_schema.MoveNext()
            rs_schema.Close()

            if not table_exists:
                logger.info(f"Table {table_name} does not exist in SawcData.mdb")
                conn.Close()
                return {"alarms": alarms, "operations": operations, "logins": logins}

            query = f"SELECT id, WriteDate, Product_Name, Batch_NO, GroupNO, UserID, ClassA, ClassB, ValuePre, ValueNew FROM [{table_name}] ORDER BY id ASC"
            rs, _ = conn.Execute(query)

            while not rs.EOF:
                event_id = rs.Fields(0).Value
                raw_date = rs.Fields(1).Value
                product = clean_str(rs.Fields(2).Value)
                batch_no = clean_str(rs.Fields(3).Value)
                group_no = rs.Fields(4).Value
                user_id = clean_str(rs.Fields(5).Value)
                class_a = str(rs.Fields(6).Value or "").strip().zfill(4)
                class_b = str(rs.Fields(7).Value or "").strip().zfill(4)
                val_pre = clean_str(rs.Fields(8).Value)
                val_new = clean_str(rs.Fields(9).Value)

                # Format timestamp
                timestamp_str = ""
                if hasattr(raw_date, "strftime"):
                    timestamp_str = raw_date.strftime("%Y-%m-%d %H:%M:%S")
                elif raw_date:
                    timestamp_str = str(raw_date)

                user_info = self.get_user_info(user_id)
                operator_name = user_info.get("userName") or user_id

                if class_a == "0030":
                    # Alarm event
                    alarm_msg = self.get_alarm_name(class_b)
                    severity = "CRITICAL" if any(x in alarm_msg.lower() for x in ["e.m.g", "overload", "air", "stop", "safety"]) else "WARNING"
                    alarms.append({
                        "alarmId": f"ALM-MC081-{date_clean}-{event_id}",
                        "equipmentId": "MC081",
                        "equipmentCode": "MC081",
                        "equipmentType": "COMP",
                        "stageId": "STAGE-4",
                        "timestamp": timestamp_str,
                        "classA": class_a,
                        "classB": class_b,
                        "alarmCode": class_b,
                        "alarmName": alarm_msg,
                        "description": alarm_msg,
                        "severity": severity,
                        "productName": product,
                        "batchNo": batch_no,
                        "userId": user_id,
                        "operatorName": operator_name
                    })

                elif class_a == "0010":
                    # Operation event (Audit trail)
                    op_name = self.get_operation_name(class_b)
                    operations.append({
                        "auditId": f"AUD-MC081-{date_clean}-{event_id}",
                        "equipmentId": "MC081",
                        "equipmentCode": "MC081",
                        "equipmentType": "COMP",
                        "stageId": "STAGE-4",
                        "timestamp": timestamp_str,
                        "eventType": "PARAMETER_CHANGE",
                        "action": op_name,
                        "classA": class_a,
                        "classB": class_b,
                        "previousValue": val_pre,
                        "newValue": val_new,
                        "productName": product,
                        "batchNo": batch_no,
                        "userId": user_id,
                        "operatorName": operator_name
                    })

                elif class_a == "0000":
                    # Login/Logout event
                    is_login = (class_b == "0010")
                    action = "USER_LOGIN" if is_login else "USER_LOGOUT"
                    logins.append({
                        "loginId": f"LOG-MC081-{date_clean}-{event_id}",
                        "auditId": f"LOG-MC081-{date_clean}-{event_id}",
                        "equipmentId": "MC081",
                        "equipmentCode": "MC081",
                        "equipmentType": "COMP",
                        "stageId": "STAGE-4",
                        "timestamp": timestamp_str,
                        "eventType": action,
                        "action": f"{action} - {user_id}",
                        "userId": user_id,
                        "operatorName": operator_name,
                        "productName": product,
                        "batchNo": batch_no
                    })

                rs.MoveNext()

            rs.Close()
            conn.Close()
        except Exception as e:
            logger.error(f"Error reading {table_name} from SawcData.mdb: {e}", exc_info=True)

        return {
            "alarms": alarms,
            "operations": operations,
            "logins": logins
        }

    def read_awc_telemetry(self, target_date_str: str, limit: Optional[int] = None) -> List[Dict[str, Any]]:
        """
        Reads AWC_<YYYYMMDD> table from SawcData.mdb for high-resolution compression telemetry.
        """
        date_clean = target_date_str.replace("-", "")
        table_name = f"AWC_{date_clean}"
        telemetry: List[Dict[str, Any]] = []

        if not os.path.exists(self.sawc_data_mdb_path):
            return telemetry

        try:
            conn = self._get_connection(self.sawc_data_mdb_path)
            rs_schema = conn.OpenSchema(20)
            table_exists = False
            while not rs_schema.EOF:
                if rs_schema.Fields("TABLE_NAME").Value == table_name:
                    table_exists = True
                    break
                rs_schema.MoveNext()
            rs_schema.Close()

            if not table_exists:
                conn.Close()
                return telemetry

            top_clause = f"TOP {limit}" if limit else ""
            query = f"SELECT {top_clause} id, WriteDate, StationNO, RotationNO, PP_MeanData1, PP_MaxData1, PP_MinData1, PP_SDData1, MP_MeanData1, MP_MaxData1, MP_MinData1, MP_SDData1, UPT_MeanData, LPT_MeanData, EF_MeanData, AWC_Mean1 FROM [{table_name}] ORDER BY id ASC"
            rs, _ = conn.Execute(query)

            while not rs.EOF:
                row_id = rs.Fields(0).Value
                raw_date = rs.Fields(1).Value
                station_no = rs.Fields(2).Value
                rotation_no = rs.Fields(3).Value

                ts_str = raw_date.strftime("%Y-%m-%d %H:%M:%S") if hasattr(raw_date, "strftime") else str(raw_date)

                telemetry.append({
                    "id": row_id,
                    "timestamp": ts_str,
                    "stationNo": station_no,
                    "rotationNo": rotation_no,
                    "prePressureMean": parse_numeric(rs.Fields(4).Value),
                    "prePressureMax": parse_numeric(rs.Fields(5).Value),
                    "prePressureMin": parse_numeric(rs.Fields(6).Value),
                    "prePressureSd": parse_numeric(rs.Fields(7).Value),
                    "mainPressureMean": parse_numeric(rs.Fields(8).Value),
                    "mainPressureMax": parse_numeric(rs.Fields(9).Value),
                    "mainPressureMin": parse_numeric(rs.Fields(10).Value),
                    "mainPressureSd": parse_numeric(rs.Fields(11).Value),
                    "upperPunchTightness": parse_numeric(rs.Fields(12).Value),
                    "lowerPunchTightness": parse_numeric(rs.Fields(13).Value),
                    "ejectionForce": parse_numeric(rs.Fields(14).Value),
                    "awcMean": parse_numeric(rs.Fields(15).Value)
                })
                rs.MoveNext()

            rs.Close()
            conn.Close()
        except Exception as e:
            logger.error(f"Error reading {table_name}: {e}")

        return telemetry
