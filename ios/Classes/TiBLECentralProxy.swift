/**
 * Appcelerator Titanium Mobile - Bluetooth Low Energy (BLE) Module
 * Copyright (c) 2020 by Axway, Inc. All Rights Reserved.
 * Proprietary and Confidential - This source code is not for redistribution
 */

import Foundation
import TitaniumKit
import CoreBluetooth

@objc
class TiBLECentralProxy: TiProxy {
    private var _central: CBCentral!

    private override init() {
        super.init()
    }

    convenience init(pageContext: TiEvaluator, central: CBCentral) {
        self.init()
        _init(withPageContext: pageContext)
        _central = central
    }
    @objc
    func maximumUpdateValueLength() -> NSNumber {
        return NSNumber(value: _central.maximumUpdateValueLength)
    }
    @objc
    func address() -> String {
        return _central.identifier.uuidString
    }
    func central() -> CBCentral {
        return _central
    }
}
