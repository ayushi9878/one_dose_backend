#!/usr/bin/env bash
#
# One-time GCP setup for CareFlow: Artifact Registry, the GCE VM, Secret
# Manager entries and the least-privilege IAM bindings Cloud Build needs.
#
# Review the variables below, authenticate with `gcloud auth login`, then run.
# Every command is idempotent enough to re-run safely.

set -euo pipefail

PROJECT_ID="${PROJECT_ID:?export PROJECT_ID before running}"
REGION="${REGION:-asia-south2}"
ZONE="${ZONE:-asia-south2-b}"
REPOSITORY="${REPOSITORY:-careflow-repo}"
VM_NAME="${VM_NAME:-onedose-vm}"
MACHINE_TYPE="${MACHINE_TYPE:-e2-medium}"

gcloud config set project "${PROJECT_ID}"

echo "==> Enabling the required APIs"
gcloud services enable \
    artifactregistry.googleapis.com \
    cloudbuild.googleapis.com \
    compute.googleapis.com \
    secretmanager.googleapis.com \
    iap.googleapis.com

echo "==> Creating the Artifact Registry Docker repository"
gcloud artifacts repositories create "${REPOSITORY}" \
    --repository-format=docker \
    --location="${REGION}" \
    --description="CareFlow container images" \
    2>/dev/null || echo "    repository already exists"

echo "==> Creating the GCE VM"
# The VM carries no service-account key: Cloud Build reaches it over IAP-tunnelled
# SSH, and the VM pulls images using its own attached service account.
gcloud compute instances create "${VM_NAME}" \
    --zone="${ZONE}" \
    --machine-type="${MACHINE_TYPE}" \
    --image-family=ubuntu-2204-lts \
    --image-project=ubuntu-os-cloud \
    --boot-disk-size=30GB \
    --boot-disk-type=pd-balanced \
    --scopes=https://www.googleapis.com/auth/cloud-platform \
    --tags=careflow-web \
    --metadata=enable-oslogin=TRUE \
    2>/dev/null || echo "    instance already exists"

echo "==> Configuring firewall rules"
# Only HTTP/HTTPS from the internet. Port 8080 is never opened; Spring Boot
# binds to loopback and is reachable solely through Nginx.
gcloud compute firewall-rules create careflow-allow-web \
    --allow=tcp:80,tcp:443 \
    --target-tags=careflow-web \
    --description="Public HTTP/HTTPS for CareFlow" \
    2>/dev/null || echo "    web rule already exists"

# SSH restricted to Google's IAP forwarding range rather than the open internet.
gcloud compute firewall-rules create careflow-allow-iap-ssh \
    --allow=tcp:22 \
    --source-ranges=35.235.240.0/20 \
    --target-tags=careflow-web \
    --description="SSH via IAP tunnel only" \
    2>/dev/null || echo "    SSH rule already exists"

echo "==> Creating Secret Manager entries"
# Values are supplied interactively so they never appear in this script,
# in shell history, or in the repository.
for secret in careflow-db-password careflow-jwt-secret; do
    if ! gcloud secrets describe "${secret}" >/dev/null 2>&1; then
        echo "    creating ${secret} — paste the value, then press Ctrl-D:"
        gcloud secrets create "${secret}" --replication-policy=automatic --data-file=-
    else
        echo "    ${secret} already exists"
    fi
done

PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
CLOUD_BUILD_SA="${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"
COMPUTE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

echo "==> Granting least-privilege IAM roles"

# Cloud Build: push images, reach the VM over IAP SSH, and read secrets.
for role in \
    roles/artifactregistry.writer \
    roles/compute.instanceAdmin.v1 \
    roles/iap.tunnelResourceAccessor \
    roles/iam.serviceAccountUser \
    roles/secretmanager.secretAccessor
do
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${CLOUD_BUILD_SA}" \
        --role="${role}" \
        --condition=None \
        --quiet >/dev/null
    echo "    cloudbuild -> ${role}"
done

# The VM only needs to pull images and read its own secrets.
for role in roles/artifactregistry.reader roles/secretmanager.secretAccessor; do
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${COMPUTE_SA}" \
        --role="${role}" \
        --condition=None \
        --quiet >/dev/null
    echo "    compute -> ${role}"
done

cat <<NEXT

GCP setup complete.

  Artifact Registry: ${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}
  VM:                ${VM_NAME} (${ZONE})

Next:
  1. Provision the VM:
       gcloud compute ssh ${VM_NAME} --zone ${ZONE} --tunnel-through-iap
       sudo bash provision-vm.sh <your-domain>
  2. Write /opt/onedose/one_dose_backend/deployment/.env from Secret Manager, chmod 600.
  3. Ensure the repo is checked out at /opt/onedose/one_dose_backend and that
     deployment/gce/deploy.sh is executable.
  4. Connect the Cloud Build trigger to GitHub:
       gcloud builds triggers create github \\
         --name=careflow-main \\
         --repo-owner=<owner> --repo-name=<repo> \\
         --branch-pattern='^main$' \\
         --build-config=cloudbuild.yaml

Security notes:
  - No SSH private key is stored in the repository or in Cloud Build; access is
    via IAP-tunnelled SSH authenticated by the Cloud Build service account.
  - Secrets live in Secret Manager, never in source or image layers.
  - SSH is restricted to Google's IAP range (35.235.240.0/20).
NEXT
