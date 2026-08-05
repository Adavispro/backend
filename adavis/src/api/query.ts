export type QueryValue = boolean | number | string | null | undefined;

export type QueryParams = object;

export const withQuery = (path: string, query?: QueryParams) => {
  if (!query) return path;

  const params = new URLSearchParams();

  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    if (!["boolean", "number", "string"].includes(typeof value)) return;
    params.set(key, String(value));
  });

  const queryString = params.toString();
  return queryString ? `${path}?${queryString}` : path;
};
