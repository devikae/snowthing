import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Snowthing Mockup",
  description: "Snowboard community product dashboard mockup"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
