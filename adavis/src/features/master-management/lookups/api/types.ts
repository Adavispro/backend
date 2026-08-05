export interface MasterLookupOption {
  label: string;
  value: string;
}

export interface MasterLookupOptions {
  departments: MasterLookupOption[];
  groups: MasterLookupOption[];
  roles: MasterLookupOption[];
  users: MasterLookupOption[];
}
