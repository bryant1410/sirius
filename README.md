# Sirius

[![Build Status](https://travis-ci.org/Comcast/sirius.svg?branch=master)](https://travis-ci.org/Comcast/sirius)

Sirius is a library for distributing and coordinating data updates
amongst a cluster of nodes. It handles building an absolute ordering
for updates that arrive in the cluster, ensuring that cluster nodes
eventually receive all updates, and persisting the updates on each
node. These updates are generally used to build in-memory data
structures on each node, allowing applications using Sirius to have
direct access to native data structures representing up-to-date data.
Sirius does not, however, build these data structures
itself -- instead, the client application supplies a callback handler,
which allows developers using Sirius to build whatever structures are
most appropriate for their application.

Said another way: Sirius enables a cluster of nodes to keep
developer-controlled in-memory data structures eventually consistent,
allowing I/O-free access to shared information.

#### Next Steps
* [Sirius Wiki](https://github.com/Comcast/sirius/wiki)
* [Getting started with Sirius](https://github.com/Comcast/sirius/wiki/Getting+started+with+Sirius)
* [Configuring Sirius](https://github.com/Comcast/sirius/wiki/Configuring+Sirius)
* [How to deploy Sirius](https://github.com/Comcast/sirius/wiki/How+to+deploy+Sirius)
* [Contributing Guidelines](https://github.com/Comcast/sirius/blob/master/CONTRIBUTING.md)
* [License](https://github.com/Comcast/sirius/blob/master/LICENSE)

#### Questions, Comments, Bugs and More
* [Found a bug or file a feature request](https://github.com/Comcast/sirius/issues)
* [Ask a question about using Sirius](http://stackoverflow.com/search?tab=newest&q=sirius)