"""
Core Dataclasses and Domain Models for the API Ingestion Pipeline.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Dict, List, Optional


@dataclass
class BatchDetails:
    batch_no: str
    lot_no: str
    product_name: str
    product_code: str
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    status: str = "IN_PROGRESS"
    raw: Dict[str, Any] = field(default_factory=dict)


@dataclass
class OperationalEvent:
    observed_at: datetime
    batch_no: str
    lot_no: str
    equipment_code: str
    equipment_type: str
    status: str = ""
    operator_name: str = ""
    metrics: Dict[str, float] = field(default_factory=dict)
    raw: Dict[str, Any] = field(default_factory=dict)


@dataclass
class AlarmEvent:
    occurred_time: datetime
    alarm_name: str
    equipment_code: str
    resolved_time: Optional[datetime] = None
    duration: str = "-"
    state_after: int = 0
    status: str = "ACTIVE"
    raw: Dict[str, Any] = field(default_factory=dict)


@dataclass
class AuditEvent:
    event_time: datetime
    record_id: str
    user_name: str
    user_role: str
    description: str
    equipment_code: str
    batch_no: str = ""
    lot_no: str = ""
    old_value: str = "-"
    new_value: str = "-"
    reason: str = "-"
    raw: Dict[str, Any] = field(default_factory=dict)


@dataclass
class StageStatus:
    stage_id: str
    stage_name: str
    equipment_type: str
    equipment_code: str
    sequence_order: int
    execution_status: str = "NOT_STARTED"
    stage_start_at: Optional[datetime] = None
    stage_end_at: Optional[datetime] = None
    operator_name: str = ""
    supervisor_name: str = ""
    record_count: int = 0
    approval_status: str = "PENDING"
    approval_by: str = ""
    approval_at: Optional[datetime] = None
    comments: str = ""


@dataclass
class IngestionCycleResult:
    cycle_time: datetime
    total_assets: int
    successful_assets: int
    failed_assets: int
    total_batches_processed: int
    total_operational_events: int
    total_alarms: int
    total_audits: int
    errors: List[str] = field(default_factory=list)
