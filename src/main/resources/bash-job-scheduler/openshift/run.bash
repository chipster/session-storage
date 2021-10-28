stdin=$(</dev/stdin)
echo "start container, slots: $SLOTS, image: $IMAGE, sessionId: $SESSION_ID, jobId: $JOB_ID"

if kubectl get pod $POD_NAME > /dev/null 2>&1; then
  echo "pod exists already"
  exit 1
fi
memory=$(( SLOTS*8 ))Gi
cpu_limit=$(( SLOTS*2 ))
cpu_request=$SLOTS

jq_patch=".metadata.name=\"$POD_NAME\" |
  .spec.containers[0].image=\"$IMAGE\" |
  .spec.containers[0].command += [\"$SESSION_ID\", \"$JOB_ID\", \"$SESSION_TOKEN\", \"$COMP_TOKEN\"] |
  .spec.containers[0].resources.limits.cpu=\"$cpu_limit\" | 
  .spec.containers[0].resources.limits.memory=\"$memory\" |
  .spec.containers[0].resources.requests.cpu=\"$cpu_request\" | 
  .spec.containers[0].resources.requests.memory=\"$memory\""

job_json=$(echo "$stdin" | yq e - -o=json | jq "$jq_patch")

echo "$job_json" | kubectl apply -f -