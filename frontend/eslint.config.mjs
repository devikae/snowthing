import nextVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

const eslintConfig = [
  { ignores: ["public/vivaldi.js"] },
  ...nextVitals,
  ...nextTypescript,
  {
    // 브라우저 기본 알림창 대신 app/lib/feedback의 toast, confirm을 쓴다(IKA-119). 남겨야 하면 이유를 적은 주석으로 예외를 둔다.
    rules: {
      "no-alert": "error",
      "no-restricted-globals": [
        "error",
        ...["alert", "confirm", "prompt"].map((name) => ({ name, message: "app/lib/feedback의 toast, confirm을 쓴다." })),
      ],
    },
  },
];

export default eslintConfig;
