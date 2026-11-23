import hashlib
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys


REPO = os.environ["GITHUB_REPOSITORY"]
SHA = os.environ["GITHUB_SHA"]
RELEASES = f"repos/{REPO}/releases"
PROJECT_URL = f"{os.environ['GITHUB_SERVER_URL']}/{REPO}"
DRAFT_MARKER = "<!-- emitloom-android-release -->"
VERSION_TAG = re.compile(r"v([1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def run(*command, data=None):
    return subprocess.check_output(command, input=data, encoding="utf-8").strip()


def api(path, *options, payload=None):
    if payload is None:
        return json.loads(run("gh", "api", path, *options))
    return json.loads(run("gh", "api", path, *options, "--input", "-", data=json.dumps(payload)))


def releases():
    return [release for page in api(f"{RELEASES}?per_page=100", "--paginate", "--slurp") for release in page]


def is_main_head():
    head = api(f"repos/{REPO}/git/ref/heads/main")["object"]["sha"]
    if head != SHA:
        print(f"Skipping {SHA[:12]} because main has advanced to {head[:12]}.")
    return head == SHA


def plan():
    if not is_main_head():
        return

    tags = {}
    for tag in run("git", "tag", "--list", "v*").splitlines():
        match = VERSION_TAG.fullmatch(tag)
        if match:
            tags[tag] = tuple(map(int, match.groups()))

    published = {
        release["tag_name"]: release for release in releases()
        if not release["draft"] and not release["prerelease"] and release["tag_name"] in tags
    }
    reachable = set(run("git", "tag", "--merged", SHA).splitlines())
    previous = max(reachable & published.keys(), key=tags.get, default="")
    if previous and run("git", "rev-parse", f"{previous}^{{commit}}") == SHA:
        if any(asset["state"] == "uploaded" and asset["name"].endswith(".apk")
               for asset in published[previous]["assets"]):
            print(f"{previous} already has an APK for this commit.")
            return
        previous = ""

    major, minor, patch = max(tags.values(), default=(1, 0, 0))
    patch += 1
    version = f"{major}.{minor}.{patch}"
    # Reserve separate ranges for future major and minor versions, starting at 1 for v1.0.0.
    version_code = (major - 1) * 10_000_000 + minor * 100_000 + patch + 1
    if minor >= 100 or patch >= 100_000 or version_code > 2_100_000_000:
        raise ValueError(f"Version {version} exceeds the Android version code range for EmitLoom.")

    if not previous:
        before = os.environ.get("PREVIOUS_PUSH", "")
        if re.fullmatch(r"[0-9a-f]{40}", before) and before != SHA and subprocess.run(
            ["git", "merge-base", "--is-ancestor", before, SHA], capture_output=True
        ).returncode == 0:
            previous = before
        else:
            previous = run("git", "show", "-s", "--format=%P", SHA).partition(" ")[0]

    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(f"version={version}\nversion_code={version_code}\nprevious={previous}\n")
    print(f"Planned EmitLoom v{version} from {SHA[:12]}.")


def release_notes(apk, checksum):
    previous = os.environ["PREVIOUS"]
    revision_range = f"{previous}..{SHA}" if previous else SHA
    commits = run("git", "log", "--first-parent", "-11", "--format=%H%x09%s", revision_range).splitlines()
    lines = [DRAFT_MARKER, "## What's changed", ""]
    for commit in commits[:10]:
        commit_sha, _, subject = commit.partition("\t")
        subject = re.sub(r"([\\`*_\[\]~])", r"\\\1", html.escape(subject))
        lines.append(f"- {subject or 'Update EmitLoom'} ([`{commit_sha[:7]}`]({PROJECT_URL}/commit/{commit_sha}))")
    if previous:
        label = "See all changes" if len(commits) > 10 else "Full changelog"
        lines.extend(["", f"[{label}]({PROJECT_URL}/compare/{previous}...{SHA})"])
    lines.extend(["", "## Download", "", f"Install `{apk.name}` from Assets on Android 8.0 or later.",
                  f"SHA-256: `{checksum}`", ""])
    return "\n".join(lines)


def publish():
    if not is_main_head():
        return

    version = os.environ["VERSION"]
    tag = f"v{version}"
    apk = Path(f"apk-export/emitloom-{tag}-release.apk")
    with apk.open("rb") as source:
        checksum = hashlib.file_digest(source, "sha256").hexdigest()
    metadata = {"tag_name": tag, "target_commitish": SHA, "name": f"EmitLoom {tag}",
                "body": release_notes(apk, checksum)}

    matching = [release for release in releases() if release["tag_name"] == tag]
    if matching:
        draft, = matching
        if not draft["draft"] or draft["author"]["login"] != "github-actions[bot]" or not (
            draft["body"] or ""
        ).startswith(DRAFT_MARKER):
            raise RuntimeError(f"Release {tag} already exists outside this workflow. Re-run to refresh the version.")
    else:
        draft = api(RELEASES, "--method", "POST", payload={**metadata, "draft": True})

    run("gh", "release", "upload", tag, str(apk), "--clobber")
    endpoint = f"{RELEASES}/{draft['id']}"
    assets = api(endpoint)["assets"]
    if not any(asset["name"] == apk.name and asset["size"] == apk.stat().st_size
               and asset["state"] == "uploaded" and asset.get("digest") in (None, f"sha256:{checksum}")
               for asset in assets):
        raise RuntimeError(f"Could not verify the uploaded APK for {tag}.")

    refs = api(f"repos/{REPO}/git/matching-refs/tags/{tag}")
    if any(ref["ref"] == f"refs/tags/{tag}" and ref["object"]["sha"] != SHA for ref in refs):
        raise RuntimeError(f"Tag {tag} was reserved by another release. Re-run to refresh the version.")
    if is_main_head():
        release = api(endpoint, "--method", "PATCH",
                      payload={**metadata, "draft": False, "make_latest": "true"})
        print(f"Published {release['html_url']}")


if __name__ == "__main__":
    {"plan": plan, "publish": publish}[sys.argv[1]]()
