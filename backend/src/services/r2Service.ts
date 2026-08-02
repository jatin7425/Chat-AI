import { PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { config } from "../config";

const client = new S3Client({
  region: "auto",
  endpoint: `https://${config.r2AccountId}.r2.cloudflarestorage.com`,
  credentials: {
    accessKeyId: config.r2AccessKeyId,
    secretAccessKey: config.r2SecretAccessKey,
  },
});

/** Uploads a persona photo to R2 under a key namespaced by space/persona/kind, returning the public URL. */
export async function uploadImage(key: string, bytes: Buffer, contentType: string): Promise<string> {
  if (!config.r2BucketName || !config.r2PublicBaseUrl) {
    throw new Error("R2 is not configured -- set R2_BUCKET_NAME and R2_PUBLIC_BASE_URL in backend/.env");
  }
  await client.send(
    new PutObjectCommand({
      Bucket: config.r2BucketName,
      Key: key,
      Body: bytes,
      ContentType: contentType,
    })
  );
  return `${config.r2PublicBaseUrl}/${key}`;
}
