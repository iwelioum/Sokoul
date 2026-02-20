<h2><?php _z('User Requests'); ?></h2>
<p><?php _z('For this application to function correctly add the required API credentials'); ?></p>
<hr>
<table class="form-table dbmv">
    <tbody>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-input-request-email"><?php _z('Email'); ?></label>
            </th>
            <td>
                <?php $this->field_text('request-email', false, __z('Establish an email where you want to be notified of new requests')); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <?php _z('Unknown user'); ?>
            </th>
            <td>
                <?php $this->field_checkbox('requestsunk', __z('Unknown users can publish requests?')); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <?php _z('Auto publish'); ?>
            </th>
            <td>
                <?php $this->field_checkbox('reqauto-adm', __z('Administrator')); ?>
                <?php $this->field_checkbox('reqauto-edi', __z('Editor')); ?>
                <?php $this->field_checkbox('reqauto-aut', __z('Author')); ?>
                <?php $this->field_checkbox('reqauto-con', __z('Contributor')); ?>
                <?php $this->field_checkbox('reqauto-sub', __z('Subscriber')); ?>
                <?php $this->field_checkbox('reqauto-unk', __z('Unknown user')); ?>
                <p><?php _z('Mark user roles that do not require content moderation'); ?></p>
            </td>
        </tr>
    </tbody>
</table>
