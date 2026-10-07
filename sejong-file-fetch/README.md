# Sejong Compression date-based file fetch

This scheduler reads the Sejong Compression backup folder, stages original files for a selected date, and records where each file came from. It does not modify the source folder or load a database.

## Source layout and formats

The configured Windows source is `D:\ADAVIS\Sejong Data\pb1 Compression Backup`, matching the screenshots. It searches that folder recursively, including date folders directly below it and date folders under `pb1 mb\Sawc`. Check the exact folder spelling on the plant computer if the source is unavailable.

The sample folder contains three `ProductionReport-2026-09-01-...xls` files and `SawcData.mdb`. It does not contain an `.fdb` file. The `.xls` files are genuine Excel 97–2003 production reports. The MDB contains dated `SAWC_DATA_YYYYMMDD` event tables and `AWC_YYYYMMDD` measurement tables; its `ClassA`/`ClassB` tables describe event codes, including alarms.

The scheduler stages `.xls`, `.xlsx`, `.xlsm`, `.mdb`, `.accdb`, and `.fdb` originals. It ignores application executables, images, Access lock files, and spreadsheets that have no selected date in their filename or parent folders. Root database files are included so their dated tables can be exported.

## Select a date on Windows

Open Command Prompt in the extracted `sejong-file-fetch` folder:

```cmd
python "File Fetch Scheduler.py" --date 2026-09-05
```

For a range of up to 31 days:

```cmd
python "File Fetch Scheduler.py" --from-date 2026-09-05 --to-date 2026-09-30
```

For today's date:

```cmd
python "File Fetch Scheduler.py" --today
```

The source path and default date are in `config\file_fetch_config.json`. Use `--source-path "D:\actual\folder"` to override the source for one run. The scheduler runs one cycle and exits by default; set `continuous_fetch` to `true` for repeated fetches at `schedule_minutes`.

To extract readable cells from `.xls`, `.xlsx`, and `.xlsm` files as well as copying the originals, install the optional spreadsheet readers once:

```cmd
python -m pip install -r optional_requirements.txt
```

Date matching uses `YYYY-MM-DD` folders and dates embedded in filenames, such as `ProductionReport-2026-09-05-10-02-22.xls`. It does not use the Windows “Date modified” column to decide the production date, because copied backup folders may all have a newer modification time.

## Staged output

- Original dated files: `staging\raw\YYYY-MM-DD\<category>\...`
- Original Access/Firebird database snapshots: `staging\raw\databases\database\...`
- Access exports when an MDB reader is available: `staging\extracted\YYYY-MM-DD\...\audit_events.csv`, `alarms_and_safety.csv`, `login_logout.csv`, and `awc_data.csv`. The original `sawc_data.csv` and class lookup CSV files are retained alongside them.
- Spreadsheet cell exports when the optional readers are installed: `staging\extracted\YYYY-MM-DD\<category>\...\cells.json`. Production report exports include a small summary with batch number, product, machine, user, and station when those labels are present.
- File and export metadata: `staging\metadata\file_index.json`
- Cycle results and errors: `logs\file_fetch.log`

Multiple production reports on the same day keep distinct filenames. Modified source files are copied as new versions. Unchanged files are skipped, while a database can still be exported for a newly requested date.

On Windows, extracting MDB tables requires a Microsoft Access OLE DB provider (ACE 16.0/12.0, or Jet 4.0). If the provider is unavailable, the original MDB is still staged and the export failure is recorded in the log and metadata. On Linux, `mdb-tables` and `mdb-export` from MDB Tools are needed. `.fdb` files are staged as originals; extracting Firebird records requires its driver, schema, and credentials, which were not supplied. The `.xls` reports are preserved as original files, and their optional cell export is an observation aid rather than the future database schema.

The local sample MDB has dated event tables beginning on 2026-09-25, so a 2026-09-05 request against that sample will not produce audit or alarm rows. The plant MDB may contain different dates.
