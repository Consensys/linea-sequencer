// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract EcAdd {
    function callEcAdd(bytes memory input)
    public
    view
    returns (bytes memory)
    {
        uint256 callDataSize = input.length;
        bytes memory output = new bytes(32); // Allocate memory for the output

        // Several calls to ECADD per transaction
        uint callsPerTransaction = 32;
        for (uint i = 0; i < callsPerTransaction; i++) {
            assembly {
                let callDataOffset := add(input, 0x20)  // Move pointer past length prefix to actual input
                let returnAtOffset := add(output, 0x20) // Move pointer past length prefix to store output

                let success := staticcall(
                    gas(),
                    0x06, // ECADD address
                    callDataOffset,
                    callDataSize,
                    returnAtOffset,
                    0  // returnAtCapacity
                )
            }
        }
        return output;
    }
}