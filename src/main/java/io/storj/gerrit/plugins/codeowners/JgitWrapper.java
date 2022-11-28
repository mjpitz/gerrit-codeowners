// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied. See the License for the specific language governing
// permissions and limitations under the License.
//
// ---
//
// Modifications from original:
// - Updated package for project.
// - Modified getBlobAsBytes to not throw and exception and instead to return an empty optional.
//
// Copyright (C) 2022 Mya Pitzeruse
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied. See the License for the specific language governing
// permissions and limitations under the License.

package io.storj.gerrit.plugins.codeowners;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.eclipse.jgit.lib.FileMode.TYPE_FILE;
import static org.eclipse.jgit.lib.FileMode.TYPE_MASK;

public class JgitWrapper {
    private static final Logger log = LoggerFactory.getLogger(JgitWrapper.class);

    public static Optional<byte[]> getBlobAsBytes(Repository repository, String revision, String path) {
        final RevCommit commit;

        try {
            final ObjectId objectId = repository.resolve(revision);
            if (objectId == null) {
                return Optional.empty();
            }

            commit = parseCommit(repository, objectId);
        } catch (final IOException e) {
            log.error("failed to resolve revision", e);
            return Optional.empty();
        }

        try (final TreeWalk w = TreeWalk.forPath(repository, path, commit.getTree())) {
            return Optional.ofNullable(w)
                    .filter(walk -> (walk.getRawMode(0) & TYPE_MASK) == TYPE_FILE)
                    .map(walk -> walk.getObjectId(0))
                    .flatMap(id -> readBlob(repository, id));
        } catch (final IOException e) {
            log.error("failed to read file at revision", e);
            return Optional.empty();
        }
    }

    private static RevCommit parseCommit(Repository repository, ObjectId commit) throws IOException {
        try (final RevWalk walk = new RevWalk(repository)) {
            walk.setRetainBody(true);
            return walk.parseCommit(commit);
        }
    }

    private static Optional<byte[]> readBlob(Repository repository, ObjectId id) {
        try {
            return Optional.of(repository.open(id, OBJ_BLOB).getCachedBytes(Integer.MAX_VALUE));
        } catch (Exception e) {
            log.error("unexpected error while reading git object " + id, e);
            return Optional.empty();
        }
    }
}
