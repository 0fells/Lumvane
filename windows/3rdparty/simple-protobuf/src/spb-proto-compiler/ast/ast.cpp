#include "ast.h"
#include "ast-types.h"
#include "ast-messages-order.h"

void resolve_messages( proto_file & file )
{
    resolve_types( file );
    resolve_messages_order( file );
}
