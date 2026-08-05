export interface MongoObjectId {
  $oid: string;
}

export interface MongoDate {
  $date: string;
}

export type DbId = string | MongoObjectId;
export type DbDate = string | MongoDate;

export interface DbDocument {
  _id?: DbId;
}

export interface ListQuery {
  search?: string;
  page?: number;
  limit?: number;
}

export type CreatePayload<TDocument extends DbDocument> = Omit<
  TDocument,
  "_id" | "createdAt" | "updatedAt"
>;

export type UpdatePayload<TDocument extends DbDocument> = Partial<
  Omit<TDocument, "_id" | "createdAt" | "updatedAt">
>;
