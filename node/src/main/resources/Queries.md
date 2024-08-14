
MATCH {type: Block, as: block}.both('blockAt') {as: transaction}
RETURN block, transaction