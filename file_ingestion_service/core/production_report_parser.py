"""
Production Report Parser for Sejong Tablet Press (MC081).
Parses ProductionReport-YYYY-MM-DD-HH-MM-SS.xls and standard ProductionReport.xls files.
Uses 'batchNo' for uniformity with API Ingestion Service.
Produces schema with:
- meta: { batchNo, lotNo, productCode, productName, equipmentCode, equipmentId, equipmentType, stageId, stageName, operatorName, userId, status }
- metrics: {}
- compression_details: { metadata, batchInfo, recipeSettings, pressureData, operationValues, tightness, tabletChecker, tabletCounters, signatures, operation_history, login_history, alarm_history }
"""

import os
import re
import hashlib
import logging
from datetime import datetime, timezone
from typing import Dict, Any, List, Optional
import xlrd
from .cleaner import clean_str, parse_numeric, parse_datetime_val, parse_datetime, clean_obj_values

logger = logging.getLogger("compression.parser")


def derive_lot_number(batch_no: str, filename: str, report_timestamp: Optional[datetime], lot_index: Optional[int] = None) -> str:
    """Return a clean derived lot number (e.g. Lot-01, Lot-02) for the batch report."""
    if lot_index is not None and lot_index > 0:
        return f"Lot-{lot_index:02d}"
    return "Lot-01"


def _get_cell_value(sh: xlrd.sheet.Sheet, r: int, c: int, wb: Optional[xlrd.Book] = None) -> Any:
    """Safe cell accessor with bounds checking and excel date conversion."""
    if r < 0 or r >= sh.nrows or c < 0 or c >= sh.ncols:
        return ""
    cell = sh.cell(r, c)
    if cell.ctype == xlrd.XL_CELL_DATE and wb:
        try:
            dt_tuple = xlrd.xldate_as_tuple(cell.value, wb.datemode)
            return datetime(*dt_tuple).strftime("%Y-%m-%d %H:%M:%S")
        except Exception:
            return cell.value
    elif cell.ctype == xlrd.XL_CELL_NUMBER:
        if cell.value == int(cell.value):
            return int(cell.value)
        return cell.value
    return cell.value


def parse_production_report_xls(file_path: str, lot_index: Optional[int] = None, lot_no_override: Optional[str] = None) -> Dict[str, Any]:
    """
    Parses a Sejong Tablet Press Excel Production Report.
    Returns standardized format with 'meta', empty 'metrics', and 'compression_details'.
    """
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"Production report file not found: {file_path}")

    filename = os.path.basename(file_path)
    wb = xlrd.open_workbook(file_path)
    sh = wb.sheet_by_index(0)

    # Extract timestamp from filename e.g. ProductionReport-2026-09-25-05-39-27.xls
    report_timestamp = None
    fn_match = re.search(r"(\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})", filename)
    if fn_match:
        try:
            report_timestamp = datetime.strptime(fn_match.group(1), "%Y-%m-%d-%H-%M-%S")
        except Exception:
            pass

    # Metadata
    sw_version = clean_str(_get_cell_value(sh, 1, 0, wb))
    sw_ver_match = re.search(r"\(\s*([\d\.]+)\s*\)", sw_version)
    software_version = sw_ver_match.group(1) if sw_ver_match else "2.0"

    # --- Section 1: Product Information (Rows 6-10) ---
    station_no = clean_str(_get_cell_value(sh, 6, 8, wb)) or "Station 1"
    machine_name = clean_str(_get_cell_value(sh, 7, 2, wb)) or "MC081 SEJONG 49D"
    product_name = clean_str(_get_cell_value(sh, 7, 8, wb))
    user_id = clean_str(_get_cell_value(sh, 8, 2, wb))
    batch_no = clean_str(_get_cell_value(sh, 8, 8, wb))
    print_interval = parse_numeric(_get_cell_value(sh, 9, 2, wb))
    running_time = clean_str(_get_cell_value(sh, 9, 8, wb))
    total_counter = parse_numeric(_get_cell_value(sh, 10, 2, wb))
    total_running_time = clean_str(_get_cell_value(sh, 10, 8, wb))

    # --- Section 2: Setting Values (Rows 15-39) ---
    feeder_auto_pct = parse_numeric(_get_cell_value(sh, 15, 5, wb))
    feeder_manu_rpm = parse_numeric(_get_cell_value(sh, 15, 11, wb))
    filling_cam = clean_str(_get_cell_value(sh, 16, 3, wb))
    target_qty = parse_numeric(_get_cell_value(sh, 17, 5, wb))
    air_pressure_low_limit_kpa = parse_numeric(_get_cell_value(sh, 18, 5, wb))
    
    hydra_high_limit_mpa = parse_numeric(_get_cell_value(sh, 19, 5, wb))
    hydra_low_limit_mpa = parse_numeric(_get_cell_value(sh, 19, 11, wb))

    oil_s1_interval_min = parse_numeric(_get_cell_value(sh, 20, 6, wb))
    oil_s1_supply_sec = parse_numeric(_get_cell_value(sh, 20, 11, wb))
    oil_s2_interval_min = parse_numeric(_get_cell_value(sh, 21, 6, wb))
    oil_s2_supply_sec = parse_numeric(_get_cell_value(sh, 21, 11, wb))
    oil_s3_interval_min = parse_numeric(_get_cell_value(sh, 22, 6, wb))
    oil_s3_supply_sec = parse_numeric(_get_cell_value(sh, 22, 11, wb))

    powder_supply_time_sec = parse_numeric(_get_cell_value(sh, 23, 3, wb))
    initial_reject_time_sec = parse_numeric(_get_cell_value(sh, 24, 3, wb))
    upper_punch_tightness_high = clean_str(_get_cell_value(sh, 25, 6, wb))
    lower_punch_tightness_high = clean_str(_get_cell_value(sh, 26, 6, wb))
    ejecting_force_high = clean_str(_get_cell_value(sh, 27, 6, wb))

    # Limits & Stop Conditions
    hsp_pct = parse_numeric(_get_cell_value(sh, 29, 2, wb))
    hsp_kn = parse_numeric(_get_cell_value(sh, 29, 5, wb))
    hsp_stop = clean_str(_get_cell_value(sh, 29, 9, wb))

    hep_pct = parse_numeric(_get_cell_value(sh, 30, 2, wb))
    hep_kn = parse_numeric(_get_cell_value(sh, 30, 5, wb))
    hep_rot = parse_numeric(_get_cell_value(sh, 30, 9, wb))
    hep_tabs = parse_numeric(_get_cell_value(sh, 30, 11, wb))

    hcp_pct = parse_numeric(_get_cell_value(sh, 31, 2, wb))
    hcp_kn = parse_numeric(_get_cell_value(sh, 31, 5, wb))
    hcp_times = parse_numeric(_get_cell_value(sh, 31, 9, wb))

    ref_kn = parse_numeric(_get_cell_value(sh, 32, 2, wb))

    lcp_pct = parse_numeric(_get_cell_value(sh, 33, 2, wb))
    lcp_kn = parse_numeric(_get_cell_value(sh, 33, 5, wb))
    lcp_times = parse_numeric(_get_cell_value(sh, 33, 9, wb))

    lep_pct = parse_numeric(_get_cell_value(sh, 34, 2, wb))
    lep_kn = parse_numeric(_get_cell_value(sh, 34, 5, wb))
    lep_rot = parse_numeric(_get_cell_value(sh, 34, 9, wb))
    lep_tabs = parse_numeric(_get_cell_value(sh, 34, 11, wb))

    lsp_pct = parse_numeric(_get_cell_value(sh, 35, 2, wb))
    lsp_kn = parse_numeric(_get_cell_value(sh, 35, 5, wb))
    lsp_stop = clean_str(_get_cell_value(sh, 35, 9, wb))

    sd_limit_pct = parse_numeric(_get_cell_value(sh, 36, 2, wb))
    sd_stop = clean_str(_get_cell_value(sh, 36, 9, wb))

    pre_hsp_kn = parse_numeric(_get_cell_value(sh, 37, 5, wb))
    pre_hsp_stop = clean_str(_get_cell_value(sh, 37, 9, wb))

    mean_cal_range_rot = parse_numeric(_get_cell_value(sh, 38, 3, wb))
    cal_pass_rot = parse_numeric(_get_cell_value(sh, 38, 9, wb))
    empty_punch_no = clean_str(_get_cell_value(sh, 39, 2, wb))

    # --- Section 3: Pressure Data (Rows 43-47) ---
    mean_pre_pressure_kn = parse_numeric(_get_cell_value(sh, 43, 3, wb))
    min_pre_pressure_kn = parse_numeric(_get_cell_value(sh, 43, 7, wb))
    min_pre_punch_no = parse_numeric(_get_cell_value(sh, 43, 11, wb))
    pre_pressure_sd_pct = parse_numeric(_get_cell_value(sh, 44, 3, wb))
    max_pre_pressure_kn = parse_numeric(_get_cell_value(sh, 44, 7, wb))
    max_pre_punch_no = parse_numeric(_get_cell_value(sh, 44, 11, wb))

    mean_main_pressure_kn = parse_numeric(_get_cell_value(sh, 45, 3, wb))
    min_main_pressure_kn = parse_numeric(_get_cell_value(sh, 45, 7, wb))
    min_main_punch_no = parse_numeric(_get_cell_value(sh, 45, 11, wb))
    main_pressure_sd_pct = parse_numeric(_get_cell_value(sh, 46, 3, wb))
    max_main_pressure_kn = parse_numeric(_get_cell_value(sh, 46, 7, wb))
    max_main_punch_no = parse_numeric(_get_cell_value(sh, 46, 11, wb))

    filling_depth_inc_times = parse_numeric(_get_cell_value(sh, 47, 3, wb))
    filling_depth_dec_times = parse_numeric(_get_cell_value(sh, 47, 7, wb))

    # Page 1 Signatures (Row 48)
    page1_date_raw = _get_cell_value(sh, 48, 7, wb)
    page1_date = parse_datetime_val(page1_date_raw)
    operator_name_p1 = clean_str(_get_cell_value(sh, 48, 9, wb))

    # --- Page 2 / Section 4: Operation Value (Rows 58-76) ---
    disk_speed_rpm = parse_numeric(_get_cell_value(sh, 58, 3, wb))
    capacity_tabs_hr = parse_numeric(_get_cell_value(sh, 58, 10, wb))
    feeder_status = clean_str(_get_cell_value(sh, 60, 3, wb))
    feeder_speed_rpm = parse_numeric(_get_cell_value(sh, 60, 10, wb))

    pre_pressure_thickness_mm = parse_numeric(_get_cell_value(sh, 62, 3, wb))
    pre_lower_punch_pos_mm = parse_numeric(_get_cell_value(sh, 63, 3, wb))
    pre_penetration_depth_mm = parse_numeric(_get_cell_value(sh, 63, 10, wb))

    main_pressure_thickness_mm = parse_numeric(_get_cell_value(sh, 65, 3, wb))
    main_lower_punch_pos_mm = parse_numeric(_get_cell_value(sh, 66, 3, wb))
    main_penetration_depth_mm = parse_numeric(_get_cell_value(sh, 66, 10, wb))

    filling_depth_mm = parse_numeric(_get_cell_value(sh, 68, 3, wb))
    current_cam = clean_str(_get_cell_value(sh, 68, 9, wb))

    main_air_pressure_kpa = parse_numeric(_get_cell_value(sh, 69, 3, wb))
    hydraulic_pressure_mpa = parse_numeric(_get_cell_value(sh, 69, 10, wb))

    oil_s1_remain_min = parse_numeric(_get_cell_value(sh, 71, 4, wb))
    oil_s2_remain_min = parse_numeric(_get_cell_value(sh, 72, 4, wb))
    oil_s3_remain_min = parse_numeric(_get_cell_value(sh, 73, 4, wb))

    powder_status = clean_str(_get_cell_value(sh, 75, 2, wb))
    dust_collector = clean_str(_get_cell_value(sh, 75, 8, wb))
    initial_reject = clean_str(_get_cell_value(sh, 76, 2, wb))
    buzzer = clean_str(_get_cell_value(sh, 76, 8, wb))

    # --- Section 5: Tightness (Rows 78-85) ---
    upt_avg = parse_numeric(_get_cell_value(sh, 80, 3, wb))
    upt_sd = parse_numeric(_get_cell_value(sh, 80, 10, wb))
    upt_max = parse_numeric(_get_cell_value(sh, 81, 3, wb))
    upt_max_punch = parse_numeric(_get_cell_value(sh, 81, 10, wb))

    lpt_avg = parse_numeric(_get_cell_value(sh, 82, 3, wb))
    lpt_sd = parse_numeric(_get_cell_value(sh, 82, 10, wb))
    lpt_max = parse_numeric(_get_cell_value(sh, 83, 3, wb))
    lpt_max_punch = parse_numeric(_get_cell_value(sh, 83, 10, wb))

    ef_avg = parse_numeric(_get_cell_value(sh, 84, 3, wb))
    ef_sd = parse_numeric(_get_cell_value(sh, 84, 10, wb))
    ef_max = parse_numeric(_get_cell_value(sh, 85, 3, wb))
    ef_max_punch = parse_numeric(_get_cell_value(sh, 85, 10, wb))

    # --- Section 6: Tablet Checker (Rows 87-92) ---
    tc_wt_avg = parse_numeric(_get_cell_value(sh, 89, 5, wb))
    tc_wt_max = parse_numeric(_get_cell_value(sh, 89, 7, wb))
    tc_wt_min = parse_numeric(_get_cell_value(sh, 89, 9, wb))
    tc_wt_sd = parse_numeric(_get_cell_value(sh, 89, 11, wb))

    tc_thk_avg = parse_numeric(_get_cell_value(sh, 90, 5, wb))
    tc_thk_max = parse_numeric(_get_cell_value(sh, 90, 7, wb))
    tc_thk_min = parse_numeric(_get_cell_value(sh, 90, 9, wb))
    tc_thk_sd = parse_numeric(_get_cell_value(sh, 90, 11, wb))

    tc_dia_avg = parse_numeric(_get_cell_value(sh, 91, 5, wb))
    tc_dia_max = parse_numeric(_get_cell_value(sh, 91, 7, wb))
    tc_dia_min = parse_numeric(_get_cell_value(sh, 91, 9, wb))
    tc_dia_sd = parse_numeric(_get_cell_value(sh, 91, 11, wb))

    tc_hd_avg = parse_numeric(_get_cell_value(sh, 92, 5, wb))
    tc_hd_max = parse_numeric(_get_cell_value(sh, 92, 7, wb))
    tc_hd_min = parse_numeric(_get_cell_value(sh, 92, 9, wb))
    tc_hd_sd = parse_numeric(_get_cell_value(sh, 92, 11, wb))

    # --- Section 7: Tablet Counters (Rows 96-99) ---
    parsed_total_counter = parse_numeric(_get_cell_value(sh, 96, 4, wb))
    final_total_counter = parsed_total_counter if parsed_total_counter is not None else total_counter
    awc_counter = parse_numeric(_get_cell_value(sh, 97, 4, wb))
    
    hep_text = clean_str(_get_cell_value(sh, 98, 4, wb))
    lep_text = clean_str(_get_cell_value(sh, 98, 10, wb))
    good_text = clean_str(_get_cell_value(sh, 99, 4, wb))

    hep_count = parse_numeric(hep_text.split()[0]) if hep_text else None
    lep_count = parse_numeric(lep_text.split()[0]) if lep_text else None
    good_count = parse_numeric(good_text.split()[0]) if good_text else None

    # Page 2 Signatures (Row 100)
    page2_date_raw = _get_cell_value(sh, 100, 7, wb)
    page2_date = parse_datetime_val(page2_date_raw)
    operator_name_p2 = clean_str(_get_cell_value(sh, 100, 9, wb))

    operator_name = operator_name_p2 or operator_name_p1 or user_id
    report_date = page2_date or page1_date or (report_timestamp.strftime("%Y-%m-%d") if report_timestamp else "")
    observed_at_str = report_timestamp.strftime("%Y-%m-%d %H:%M:%S") if report_timestamp else str(report_date)
    derived_lot_no = lot_no_override or (f"Lot-{lot_index:02d}" if lot_index is not None and lot_index > 0 else derive_lot_number(batch_no, filename, report_timestamp, lot_index))

    # Complete compression_details dictionary
    compression_details = {
        "metadata": {
            "sourceFile": filename,
            "parsedAt": datetime.now(timezone.utc).isoformat(),
            "softwareVersion": software_version,
            "reportTimestamp": report_timestamp.isoformat() if report_timestamp else None,
            "reportDate": str(report_date)
        },
        "batchInfo": {
            "equipmentId": "MC081",
            "machineName": machine_name,
            "equipmentType": "COMP",
            "stageId": "STAGE-4",
            "stageName": "Compression",
            "productName": product_name,
            "batchNo": batch_no,
            "derivedLotNo": derived_lot_no,
            "stationNo": station_no,
            "userId": user_id,
            "operatorName": operator_name,
            "printInterval": print_interval,
            "runningTime": running_time,
            "totalRunningTime": total_running_time
        },
        "recipeSettings": {
            "feeder": {
                "autoPercent": feeder_auto_pct,
                "manualRpm": feeder_manu_rpm
            },
            "fillingCam": filling_cam,
            "targetQuantity": target_qty,
            "airPressureLowLimitKpa": air_pressure_low_limit_kpa,
            "hydraulicPressureLimits": {
                "highLimitMpa": hydra_high_limit_mpa,
                "lowLimitMpa": hydra_low_limit_mpa
            },
            "oilLubrication": {
                "upperPunchS1": {"intervalMin": oil_s1_interval_min, "supplySec": oil_s1_supply_sec},
                "lowerPunchS2": {"intervalMin": oil_s2_interval_min, "supplySec": oil_s2_supply_sec},
                "lowerHeadS3": {"intervalMin": oil_s3_interval_min, "supplySec": oil_s3_supply_sec}
            },
            "powderSupplyTimeSec": powder_supply_time_sec,
            "initialRejectTimeSec": initial_reject_time_sec,
            "upperPunchTightnessHigh": upper_punch_tightness_high,
            "lowerPunchTightnessHigh": lower_punch_tightness_high,
            "ejectingForceHigh": ejecting_force_high,
            "controlLimits": {
                "hsp": {"percent": hsp_pct, "kn": hsp_kn, "stop": hsp_stop},
                "hep": {"percent": hep_pct, "kn": hep_kn, "rot": hep_rot, "tabs": hep_tabs},
                "hcp": {"percent": hcp_pct, "kn": hcp_kn, "times": hcp_times},
                "ref": {"kn": ref_kn},
                "lcp": {"percent": lcp_pct, "kn": lcp_kn, "times": lcp_times},
                "lep": {"percent": lep_pct, "kn": lep_kn, "rot": lep_rot, "tabs": lep_tabs},
                "lsp": {"percent": lsp_pct, "kn": lsp_kn, "stop": lsp_stop},
                "sdLimit": {"percent": sd_limit_pct, "stop": sd_stop},
                "preHsp": {"kn": pre_hsp_kn, "stop": pre_hsp_stop},
                "meanCalculationRangeRot": mean_cal_range_rot,
                "calculationPassRot": cal_pass_rot,
                "emptyPunchNo": empty_punch_no
            }
        },
        "pressureData": {
            "prePressure": {
                "meanKn": mean_pre_pressure_kn,
                "sdPercent": pre_pressure_sd_pct,
                "minKn": min_pre_pressure_kn,
                "minPunchNo": min_pre_punch_no,
                "maxKn": max_pre_pressure_kn,
                "maxPunchNo": max_pre_punch_no
            },
            "mainPressure": {
                "meanKn": mean_main_pressure_kn,
                "sdPercent": main_pressure_sd_pct,
                "minKn": min_main_pressure_kn,
                "minPunchNo": min_main_punch_no,
                "maxKn": max_main_pressure_kn,
                "maxPunchNo": max_main_punch_no
            },
            "fillingDepthAdjustments": {
                "increaseTimes": filling_depth_inc_times,
                "decreaseTimes": filling_depth_dec_times
            }
        },
        "operationValues": {
            "diskSpeedRpm": disk_speed_rpm,
            "capacityTabsPerHour": capacity_tabs_hr,
            "feeder": {
                "status": feeder_status,
                "speedRpm": feeder_speed_rpm
            },
            "prePressure": {
                "thicknessMm": pre_pressure_thickness_mm,
                "lowerPunchPositionMm": pre_lower_punch_pos_mm,
                "penetrationDepthMm": pre_penetration_depth_mm
            },
            "mainPressure": {
                "thicknessMm": main_pressure_thickness_mm,
                "lowerPunchPositionMm": main_lower_punch_pos_mm,
                "penetrationDepthMm": main_penetration_depth_mm
            },
            "fillingDepthMm": filling_depth_mm,
            "currentCam": current_cam,
            "mainAirPressureKpa": main_air_pressure_kpa,
            "hydraulicPressureMpa": hydraulic_pressure_mpa,
            "lubricationRemainingMin": {
                "upperPunchS1": oil_s1_remain_min,
                "lowerPunchS2": oil_s2_remain_min,
                "lowerHeadS3": oil_s3_remain_min
            },
            "auxiliaryStatus": {
                "powderStatus": powder_status,
                "dustCollector": dust_collector,
                "initialReject": initial_reject,
                "buzzer": buzzer
            }
        },
        "tightness": {
            "upperPunch": {
                "averageKn": upt_avg,
                "sdPercent": upt_sd,
                "maxKn": upt_max,
                "maxPunch": upt_max_punch
            },
            "lowerPunch": {
                "averageKn": lpt_avg,
                "sdPercent": lpt_sd,
                "maxKn": lpt_max,
                "maxPunch": lpt_max_punch
            },
            "ejectionForce": {
                "averageKn": ef_avg,
                "sdPercent": ef_sd,
                "maxKn": ef_max,
                "maxPunch": ef_max_punch
            }
        },
        "tabletChecker": {
            "weightMg": {
                "average": tc_wt_avg,
                "max": tc_wt_max,
                "min": tc_wt_min,
                "sdPercent": tc_wt_sd
            },
            "thicknessMm": {
                "average": tc_thk_avg,
                "max": tc_thk_max,
                "min": tc_thk_min,
                "sdPercent": tc_thk_sd
            },
            "diameterMm": {
                "average": tc_dia_avg,
                "max": tc_dia_max,
                "min": tc_dia_min,
                "sdPercent": tc_dia_sd
            },
            "hardnessN": {
                "average": tc_hd_avg,
                "max": tc_hd_max,
                "min": tc_hd_min,
                "sdPercent": tc_hd_sd
            }
        },
        "tabletCounters": {
            "totalCounter": final_total_counter,
            "awcCounter": awc_counter,
            "hep": {"raw": hep_text, "count": hep_count},
            "lep": {"raw": lep_text, "count": lep_count},
            "good": {"raw": good_text, "count": good_count}
        },
        "signatures": {
            "operatorName": operator_name,
            "reportDate": str(report_date)
        },
        "operation_history": [],
        "login_history": [],
        "alarm_history": []
    }

    # Root payload with empty metrics and compression_details
    full_payload = {
        "observedAt": observed_at_str,
        "event_time": observed_at_str,
        "meta": {
            "batchNo": batch_no,
            "lotNo": derived_lot_no,
            "derivedLotNo": derived_lot_no,
            "productCode": product_name,
            "productName": product_name,
            "equipmentCode": "MC081",
            "equipmentId": "MC081",
            "equipmentType": "COMP",
            "stageId": "STAGE-4",
            "stageName": "Compression",
            "operatorName": operator_name,
            "userId": user_id,
            "status": "COMPLETED"
        },
        "metrics": {},
        "compression_details": compression_details
    }

    return clean_obj_values(full_payload)
