<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('WP Mail & Forms'),
        'parent' => 'settings',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'type'    => 'heading',
                'content' => __z('WP Mail')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Welcome message')
            ),
            array(
                'id'      => 'welcomesjt',
                'type'    => 'text',
                'title'   => __z('Subject'),
                'default' => __z('Welcome to {sitename}')
            ),
            array(
                'id'      => 'welcomemsg',
                'type'    => 'textarea',
                'title'   => __z('Message'),
                'default' => __z('Hello {username}, Thank you for registering at {sitename}'),
                'after'   => '<p><strong>Tags:</strong> <code>{sitename}</code> <code>{siteurl}</code> <code>{username}</code> <code>{password}</code> <code>{email}</code> <code>{first_name}</code> <code>{last_name}</code></p>',
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('SMTP Settings')
            ),
            array(
                'id'      => 'smtp',
                'type'    => 'switcher',
                'title'   => __z('Enable SMTP'),
                'label'   => __z('Configure an SMTP server for WordPress to send verified emails'),
                'default' => false
            ),
            array(
                'id'      => 'smtpserver',
                'type'    => 'text',
                'title'   => __z('SMTP Server'),
                'default' => 'smtp.gmail.com',
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'id'      => 'smtpport',
                'type'    => 'number',
                'title'   => __z('SMTP Port'),
                'default' => '587',
                'attributes' => array(
                    'style' => 'width:100px'
                ),
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'id'    => 'smtpencryp',
                'type'  => 'radio',
                'title' => __z('Type of Encryption'),
                'options' => array(
                    'plain' => __z('Plain text'),
                    'ssl'   => __z('SSL'),
                    'tsl'   => __z('TSL')
                ),
                'default'    => 'tsl',
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'id'      => 'smtpfromname',
                'type'    => 'text',
                'title'   => __z('From Name'),
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'id'      => 'smtpfromemail',
                'type'    => 'text',
                'title'   => __z('From Email Address'),
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('SMTP Authentication'),
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'id'    => 'smtpusername',
                'type'  => 'text',
                'title' => __z('Username'),
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'id'    => 'smtppassword',
                'type'  => 'text',
                'title' => __z('Password'),
                'attributes' => array('type' => 'password'),
                'dependency' => array('smtp', '==', 'true')
            ),
            array(
                'type'    => 'heading',
                'content' => __z('Report & Contact')
            ),
           array(
                'id'       => 'report_form',
                'type'     => 'switcher',
                'title'    => __z('Reports Form'),
                'subtitle' => __z('Enable report form'),
                'default'  => true
            ),
            array(
                'id' => 'report_access',
                'type' => 'radio',
                'title' => __z('Report Access'),
                'default' => 'all',
                'options' => array(
                    'all' => __z('Everyone'),
                    'registered'  => __z('Logged-in users only')
                ),
				'subtitle' => __z('Assign who can use report form'),
                'dependency' => array('report_form', '==', true)
            ),
            array(
                'id'      => 'contact_form',
                'type'    => 'switcher',
                'title'   => __z('Contact Form'),
                'subtitle'   => __z('Enable contact form'),
                'default' => true
            ),
            array(
                'id'      => 'contact_email',
                'type'    => 'text',
                'title'   => __z('Email'),
                'subtitle' => __z('Assign an email address if you want to be notified'),
                'default' => get_option('admin_email')
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Email notifications')
            ),
            array(
                'id'       => 'report_notify_email',
                'type'     => 'switcher',
                'title'    => __z('Reports'),
                'subtitle' => __z('Notify new reports by email'),
                'default'  => true
            ),
            array(
                'id'      => 'contact_notify_email',
                'type'    => 'switcher',
                'title'   => __z('Contact'),
                'subtitle'   => __z('Notify new contact messages by email'),
                'default' => true
            ),
            array(
                'type'    => 'subheading',
                'content' => __z('Firewall')
            ),
            array(
                'type'  => 'submessage',
                'style' => 'info',
                'content' => __z('We recommend not enabling more than 10 unread records per IP address, consider that this function could be used maliciously and could compromise the good status of your website.')
            ),
            array(
                'id'      => 'reports_numbers_by_ip',
                'type'    => 'slider',
                'title'   => __z('Report limit'),
                'subtitle'=> __z('Set limit of unread reports by IP address'),
                'min'     => 1,
                'max'     => 200,
                'step'    => 1,
                'default' => 10,
                'unit'    => 'Posts'
            ),
            array(
                'type'    => 'notice',
                'style'   => 'warning',
                'content' => __z('Caution, you have enabled more than 50 unread records per IP address.'),
                'dependency' => array('reports_numbers_by_ip', '>=', '50')
            ),
            array(
                'id'      => 'contact_numbers_by_ip',
                'type'    => 'slider',
                'title'   => __z('Contact messages limit'),
                'subtitle'=> __z('Set limit of unread contact messages by IP address'),
                'min'     => 1,
                'max'     => 200,
                'step'    => 1,
                'default' => 10,
                'unit'    => 'Posts'
            ),
            array(
                'type'    => 'notice',
                'style'   => 'warning',
                'content' => __z('Caution, you have enabled more than 50 unread records per IP address.'),
                'dependency' => array('contact_numbers_by_ip', '>=', '50')
            ),
            array(
                'id'     => 'whitelist',
                'type'   => 'repeater',
                'title'  => __z('Whitelist'),
                'subtitle' => __z('Create a safe list of IP addresses that can send reports without limits'),
                'fields' => array(
                    array(
                        'id'   => 'ip',
                        'type' => 'text',
                        'placeholder' => __z('IP adress')
                    ),
                )
            ),
            array(
                'id'     => 'blacklist',
                'type'   => 'repeater',
                'title'  => __z('Blacklist'),
                'subtitle' => __z('Block the sending of reports and contact messages'),
                'fields' => array(
                    array(
                        'id'   => 'ip',
                        'type' => 'text',
                        'placeholder' => __z('IP adress')
                    ),
                )
            ),
        )
    )
);
