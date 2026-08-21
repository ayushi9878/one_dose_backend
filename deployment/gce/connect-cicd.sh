#!/usr/bin/env bash
#
# Finishes wiring Cloud Build to the GCE VM.
#
# Everything here is idempotent. Run it after authorizing the Cloud Build
# GitHub App in the console (see step 0 below), which is the only part of the
# connection that cannot be automated — it needs an interactive OAuth consent.
#
#   PROJECT_ID=loyal-semiotics-480611-n2 bash deployment/gce/connect-cicd.sh

set -euo pipefail

PROJECT_ID="${PROJECT_ID:?export PROJECT_ID before running}"
REGION="${REGION:-asia-south2}"
ZONE="${ZONE:-asia-south2-b}"
VM_NAME="${VM_NAME:-onedose-vm}"
REPO_OWNER="${REPO_OWNER:-ayushi9878}"
REPO_NAME="${REPO_NAME:-one_dose_backend}"
TRIGGER_NAME="${TRIGGER_NAME:-careflow-main-deploy}"

PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
CLOUD_BUILD_SA="${PROJECT_NUMBER}@cloudbuild.gserviceaccount.com"
COMPUTE_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"

echo "==> Enabling required APIs"
gcloud services enable \
    artifactregistry.googleapis.com cloudbuild.googleapis.com \
    compute.googleapis.com secretmanager.googleapis.com iap.googleapis.com \
    --project="${PROJECT_ID}"

# The GitHub trigger runs builds as the compute service account rather than the
# default cloudbuild one, so that is the identity needing deploy rights. Resolve
# it from the trigger instead of assuming, and fall back to the cloudbuild SA if
# the trigger does not exist yet.
TRIGGER_SA="$(gcloud builds triggers describe "${TRIGGER_NAME}" \
    --project="${PROJECT_ID}" --format='value(serviceAccount)' 2>/dev/null || true)"
TRIGGER_SA="${TRIGGER_SA##*/}"
TRIGGER_SA="${TRIGGER_SA:-${CLOUD_BUILD_SA}}"

echo "==> Granting deploy roles to the build identity (${TRIGGER_SA})"
# compute.instanceAdmin.v1 + iap.tunnelResourceAccessor + serviceAccountUser are
# what let the deploy step open an IAP-tunnelled SSH session to the VM. Without
# all three the build fails at step 5 even though the image pushed cleanly.
for role in \
    roles/artifactregistry.writer \
    roles/compute.instanceAdmin.v1 \
    roles/iap.tunnelResourceAccessor \
    roles/iam.serviceAccountUser \
    roles/secretmanager.secretAccessor
do
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${TRIGGER_SA}" \
        --role="${role}" --condition=None --quiet >/dev/null
    echo "    ${TRIGGER_SA} -> ${role}"
done

echo "==> Granting the VM's service account image-pull access"
for role in roles/artifactregistry.reader roles/secretmanager.secretAccessor; do
    gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
        --member="serviceAccount:${COMPUTE_SA}" \
        --role="${role}" --condition=None --quiet >/dev/null
    echo "    compute -> ${role}"
done

echo "==> Widening the VM's OAuth scopes so it can pull from Artifact Registry"
# Scopes are a hard ceiling above IAM: without cloud-platform the VM cannot
# authenticate to Artifact Registry no matter which roles it holds. Changing
# scopes requires the instance to be stopped.
CURRENT_SCOPES="$(gcloud compute instances describe "${VM_NAME}" \
    --zone="${ZONE}" --project="${PROJECT_ID}" \
    --format='value(serviceAccounts[0].scopes.list())')"

if [[ "${CURRENT_SCOPES}" == *"cloud-platform"* ]]; then
    echo "    already has cloud-platform; leaving the instance running"
else
    echo "    stopping ${VM_NAME} to change scopes (brief downtime)"
    gcloud compute instances stop "${VM_NAME}" --zone="${ZONE}" --project="${PROJECT_ID}" --quiet
    gcloud compute instances set-service-account "${VM_NAME}" \
        --zone="${ZONE}" --project="${PROJECT_ID}" \
        --service-account="${COMPUTE_SA}" \
        --scopes=https://www.googleapis.com/auth/cloud-platform --quiet
    gcloud compute instances start "${VM_NAME}" --zone="${ZONE}" --project="${PROJECT_ID}" --quiet
    echo "    scopes updated and instance restarted"
fi

echo "==> Creating the build trigger"
if gcloud builds triggers describe "${TRIGGER_NAME}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
    echo "    trigger already exists"
else
    gcloud builds triggers create github \
        --name="${TRIGGER_NAME}" \
        --repo-owner="${REPO_OWNER}" \
        --repo-name="${REPO_NAME}" \
        --branch-pattern='^main$' \
        --build-config=cloudbuild.yaml \
        --project="${PROJECT_ID}"
fi

cat <<NEXT

Done. Verify with:
    gcloud builds triggers list --project=${PROJECT_ID}
    gcloud builds submit --config=cloudbuild.yaml --project=${PROJECT_ID} .

NEXT
