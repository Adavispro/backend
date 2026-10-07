# ADAVIS Data Fetch & Staging Module

This project contains two independent Python schedulers. The API scheduler fetches raw JSON for RMG, FBD, Coating, and Blender. The file scheduler copies original Compression equipment files from a shared path. Both stage data locally for a future DB loader; neither writes to a database.

## Requirements

- Python 3.9 or newer. The implementation uses only the Python standard library.
- Network access to the API host and the Compression share. On Linux, mount the share locally and set `network_path` to its mount point.

## Configuration

Edit `config/fetch_config.json` before running. Set `api_fetch.base_url` to the reachable plant API host, then paste a fresh access token into `api_fetch.bearer_token`. Paste only the token value, without the word `Bearer`, spaces, or Markdown escape characters. This version sends `Authorization: Bearer <token>` on dataset requests and stops with a clear message when a JWT token has expired. It does not obtain or refresh tokens. Set `file_fetch.network_path` separately if using the Compression scheduler. A different config file can be selected with `ADAVIS_FETCH_CONFIG`.

The API reference lists these assets: Blender `10012`, Coating `10021`, RMG `10094`, and FBD `10110`. Their documented `pointName` templates are in the JSON configuration and can be enabled or disabled individually. The reference's Blender labels appear reversed relative to the `Blend_Recipe` and `Blend_Op_Data` dataset names; the configuration follows the dataset names. The Coating sample URLs use different example batch numbers and lot values, so the scheduler substitutes each actual `BatchNo` and `LotNo` returned by `Batch_Info` rather than copying those examples. The reference includes alternate API hosts; select the reachable host with `base_url`.

## Run

From this folder on Windows:

```cmd
python "API Fetch Scheduler.py"
python "File Fetch Scheduler.py"
```

On Linux or macOS:

```sh
python3 "API Fetch Scheduler.py"
python3 "File Fetch Scheduler.py"
```

Each runs one cycle by default. Set the relevant `continuous_fetch` flag to `true` to run repeatedly at `schedule_minutes` (default 20). Run the schedulers as separate processes if both are needed continuously.

## Staging and selection

- Raw API responses are saved under `staging/api_raw/<asset>/<batch>/<lot>/`. The `Batch_Info` response is saved per asset and cycle. Files carry a timestamp so updated live data is retained.
- API metadata is stored in `staging/metadata/api/`. Completed batches with successful dataset sections are skipped; missing or failed sections are eligible for later retry. Live batches are fetched on later cycles when enabled. Selection favors new batches, then rotates previously selected live or incomplete batches so older work can progress. The default limit is three batches per asset per cycle.
- Compression copies are saved under `staging/files_raw/`, retaining the relative source folders. Versioned filenames prevent same day reports or modified files from overwriting earlier copies. Source paths, timestamps, sizes, checksums, status, and staged paths are recorded in `staging/metadata/files/file_index.json`.
- `COMMON` scans files directly under the source folder. `DAILY` scans date named folders using `daily_folder_format`. `AUTO` recursively discovers files in the configured source tree. Discovery and copy errors are logged; failed files remain eligible for a later cycle.
- Logs are written to `logs/api_fetch.log` and `logs/file_fetch.log`. Set `ADAVIS_LOG_LEVEL=DEBUG` for additional diagnostic output. The logs omit credential and token values.

## Troubleshooting

- If the scheduler reports that the token expired, paste a fresh bearer token into `api_fetch.bearer_token`. If the API returns HTTP 401 or 403 while the token is valid, confirm its permissions and the API host with the plant API team.
- If the API host cannot be reached, the log identifies DNS, TLS, timeout, or connection refusal when available. On Windows, run `nslookup YOUR_API_HOST` and `powershell -Command "Test-NetConnection YOUR_API_HOST -Port 443"` using the host name from `base_url`. Confirm the live host, network access, and TLS trust with the site administrator.
- If the file scheduler reports an unavailable path, mount or connect the Compression share and set `network_path` to that location.
- If an API returns an unexpected JSON shape, inspect the original response and adjust the parser or configured dataset mapping to match the actual source. Do not infer missing fields.

## Scope

This phase does not provide database connectivity, schema changes, UI changes, PDF generation changes, or batch approval workflow changes. Its output is raw staged source data and fetch metadata for observation and later DB loader work.
