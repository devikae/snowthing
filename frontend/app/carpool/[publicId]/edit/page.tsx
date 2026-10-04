import { redirect } from "next/navigation";

export default async function CarpoolEditPage({
  params,
}: {
  params: Promise<{ publicId: string }>;
}) {
  const { publicId } = await params;
  redirect(`/carpool/new?edit=${encodeURIComponent(publicId)}`);
}
