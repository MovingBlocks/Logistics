import logging
from behave import *
from kubernetes import client, config

# Configure logging
logging.basicConfig(level=logging.INFO)

@given('the WeKan Helm chart is deployed')
def step_impl(context):
    config.load_kube_config()  # Load Kubernetes configuration
    v1 = client.AppsV1Api()
    try:
        deployment = v1.read_namespaced_deployment("wekan", "wekan")
        assert deployment is not None, "Deployment not found"
        logging.info(f"Deployment 'wekan' found in 'wekan' namespace")
    except client.ApiException as e:
        assert False, f"Exception when calling AppsV1Api->read_namespaced_deployment: {e}"

@then('the WeKan pods should be running')
def step_impl(context):
    v1 = client.CoreV1Api()
    pods = v1.list_namespaced_pod("wekan")
    for pod in pods.items:
        assert pod.status.phase == "Running", f"Pod {pod.metadata.name} is not running"
        logging.info(f"Pod {pod.metadata.name} is running in 'wekan' namespace")