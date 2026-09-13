import { redirect } from "next/navigation";

export default function AnonymousBoardPage() {
  redirect("/posts?category=ANONYMOUS");
}
