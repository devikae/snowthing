#!/usr/bin/env sh
set -eu

if [ "${SPRING_PROFILES_ACTIVE:-}" = "prod" ]; then
  case "${SNOWTHING_PROD_DB_URL:-}" in
    jdbc:aws-wrapper:mysql://*) ;;
    *)
      echo "SNOWTHING_PROD_DB_URL must start with jdbc:aws-wrapper:mysql://" >&2
      exit 2
      ;;
  esac
fi

exec java -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar "$@"
